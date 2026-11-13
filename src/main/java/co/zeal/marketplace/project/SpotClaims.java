package co.zeal.marketplace.project;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Reto C-1 / REND-2 (R2-27) · Asignación atómica de cupos sin sobreasignar, agrupando las peticiones que
 * llegan a la vez.
 *
 * <p><b>Por qué no puede sobreasignar.</b> Reclamar es UNA sentencia en una transacción propia: el CTE bloquea la
 * fila de la convocatoria ({@code FOR UPDATE}), lee cuántos cupos quedan con el valor ya confirmado por la
 * transacción anterior y en la misma sentencia suma lo concedido: {@code LEAST(pedidos, cupos libres)}. No hay
 * ventana entre "leer" y "escribir", y el CHECK {@code ck_projects_no_overbooking} de la tabla es la última red.
 *
 * <p><b>Por qué se agrupa.</b> Con 500 postulaciones simultáneas, reclamar de una en una obligaba a cada petición a
 * esperar una conexión del pool (10 por réplica) dos veces —para reclamar y para guardar—, y todas hacían cola
 * detrás del bloqueo de la misma fila. Ahora las peticiones de una misma convocatoria (y empresa) que llegan juntas
 * se encolan SIN tomar conexión y un solo hilo las resuelve por lote con una sola sentencia: se serializan decenas de
 * reclamos con un solo bloqueo y un solo commit. El orden de llegada se respeta (FIFO): las primeras reciben los
 * cupos, las siguientes los puestos de la lista de espera, numerados de forma correlativa.
 *
 * <p>Cada petición solo ocupa una conexión para guardar su postulación, ya con el cupo reclamado, fuera del bloqueo.
 */
@Component
public class SpotClaims {

    /** Máximo de reclamos por sentencia: acota la sentencia y el tiempo que se retiene la fila. */
    static final int MAX_BATCH = 100;

    /** Resultado de reclamar: cupo aceptado o puesto en la lista de espera, con el estado resultante. */
    public record Claim(boolean accepted, Integer waitlistPosition, int capacity, int occupied, long version) {
    }

    private record Pending(CompletableFuture<Optional<Claim>> result) {
    }

    /** Cola de reclamos de una convocatoria (de una empresa) y el cerrojo del hilo que los resuelve. */
    private static final class Queue {
        final ConcurrentLinkedQueue<Pending> waiting = new ConcurrentLinkedQueue<>();
        final ReentrantLock resolver = new ReentrantLock();
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate shortTransaction;
    private final Timer claimTimer;
    private final DistributionSummary batchSize;
    private final ConcurrentHashMap<String, Queue> queues = new ConcurrentHashMap<>();

    public SpotClaims(JdbcTemplate jdbc, PlatformTransactionManager transactions, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.shortTransaction = new TransactionTemplate(transactions);
        this.shortTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // REND-2: cuánto tarda reclamar un cupo (espera en el lote + bloqueo de la fila + commit) y cuántos
        // reclamos resuelve cada sentencia.
        this.claimTimer = Timer.builder("zeal.marketplace.spot.claim").publishPercentileHistogram().register(meters);
        this.batchSize = DistributionSummary.builder("zeal.marketplace.spot.claim.batch.size")
                .description("Reclamos de cupo resueltos por sentencia").register(meters);
    }

    /** Vacío si la convocatoria no existe para esa empresa o ya cerró. */
    public Optional<Claim> claim(String projectId, String organization, Instant now) {
        return claimTimer.record(() -> enqueueAndResolve(projectId, organization, now));
    }

    private Optional<Claim> enqueueAndResolve(String projectId, String organization, Instant now) {
        Queue queue = queues.computeIfAbsent(organization + "|" + projectId, key -> new Queue());
        Pending mine = new Pending(new CompletableFuture<>());
        queue.waiting.add(mine);
        // Quien consigue el cerrojo resuelve un lote (con su petición y las que haya esperando); los demás
        // esperan su resultado sin tomar conexión y, si el lote ya terminó sin ellos, resuelven el siguiente.
        while (!mine.result().isDone()) {
            if (queue.resolver.tryLock()) {
                try {
                    resolveBatch(queue, projectId, organization, now);
                } finally {
                    queue.resolver.unlock();
                }
            } else {
                try {
                    return mine.result().get(1, TimeUnit.MILLISECONDS);
                } catch (TimeoutException stillWaiting) {
                    // Otro hilo está resolviendo un lote: vuelve a mirar si ya le tocó o si puede resolver él.
                } catch (java.util.concurrent.ExecutionException failed) {
                    throw unwrap(failed);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrumpido esperando un cupo", interrupted);
                }
            }
        }
        try {
            return mine.result().get();
        } catch (java.util.concurrent.ExecutionException failed) {
            throw unwrap(failed);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido esperando un cupo", interrupted);
        }
    }

    /** Resuelve hasta {@link #MAX_BATCH} reclamos en una sola sentencia y completa sus resultados. */
    private void resolveBatch(Queue queue, String projectId, String organization, Instant now) {
        List<Pending> batch = new ArrayList<>(MAX_BATCH);
        for (Pending next = queue.waiting.poll(); next != null && batch.size() < MAX_BATCH;
             next = batch.size() < MAX_BATCH ? queue.waiting.poll() : null) {
            batch.add(next);
        }
        if (batch.isEmpty()) {
            return; // Otro hilo ya resolvió los que había.
        }
        try {
            List<Claim> claims = claimBatch(projectId, organization, now, batch.size());
            batchSize.record(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).result().complete(claims.isEmpty() ? Optional.empty() : Optional.of(claims.get(i)));
            }
        } catch (RuntimeException failure) {
            batch.forEach(pending -> pending.result().completeExceptionally(failure));
        }
    }

    /**
     * Un lote de {@code count} reclamos con una sola sentencia. Devuelve lista vacía si la convocatoria no existe
     * para esa empresa o ya cerró; si no, un {@link Claim} por reclamo, en orden: los primeros {@code libres}
     * aceptados con su cupo y los demás en la lista de espera con puestos correlativos.
     */
    private List<Claim> claimBatch(String projectId, String organization, Instant now, int count) {
        try {
            return shortTransaction.execute(status -> {
                List<long[]> rows = jdbc.query("""
                        WITH current AS (
                            SELECT id, capacity, occupied, waitlisted, version FROM projects
                            WHERE id = ? AND organization = ? AND closes_at > ?
                            FOR UPDATE
                        ), granted AS (
                            SELECT id, LEAST(?::int, GREATEST(capacity - occupied, 0)) AS spots FROM current
                        )
                        UPDATE projects p SET
                            occupied   = p.occupied   + granted.spots,
                            version    = p.version    + granted.spots,
                            waitlisted = p.waitlisted + (?::int - granted.spots)
                        FROM current, granted
                        WHERE p.id = current.id AND granted.id = current.id
                        RETURNING current.capacity, current.occupied, current.waitlisted, current.version""",
                        (rs, row) -> new long[]{rs.getInt("capacity"), rs.getInt("occupied"), rs.getInt("waitlisted"),
                                rs.getLong("version")},
                        projectId, organization, Timestamp.from(now), count, count);
                if (rows.isEmpty()) {
                    return List.<Claim>of();
                }
                long[] before = rows.getFirst();
                int capacity = (int) before[0];
                int occupiedBefore = (int) before[1];
                int waitlistedBefore = (int) before[2];
                long versionBefore = before[3];
                int free = Math.min(count, Math.max(capacity - occupiedBefore, 0));
                List<Claim> claims = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    claims.add(i < free
                            ? new Claim(true, null, capacity, occupiedBefore + i + 1, versionBefore + i + 1)
                            : new Claim(false, waitlistedBefore + (i - free) + 1, capacity, occupiedBefore + free,
                                    versionBefore + free));
                }
                return claims;
            });
        } catch (DataAccessException e) {
            // lock_timeout (SQLSTATE 55P03): demasiada contención sobre la convocatoria. Respuesta 409 de
            // "intenta de nuevo", nunca un 500 ni una asignación a medias (la sentencia no se aplicó).
            if (e.getMostSpecificCause() instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                throw new CannotAcquireLockException("Contención en la convocatoria " + projectId, e);
            }
            throw e;
        }
    }

    private static RuntimeException unwrap(java.util.concurrent.ExecutionException failed) {
        return failed.getCause() instanceof RuntimeException runtime ? runtime : new IllegalStateException(failed);
    }

    /** Compensación: devuelve un cupo aceptado cuya postulación no se pudo guardar (p. ej. doble envío). */
    public void release(String projectId) {
        shortTransaction.executeWithoutResult(status -> jdbc.update(
                "UPDATE projects SET occupied = occupied - 1, version = version + 1 WHERE id = ? AND occupied > 0",
                projectId));
    }
}
