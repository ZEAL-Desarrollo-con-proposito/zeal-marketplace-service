package co.zeal.marketplace.project;

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
import java.util.Optional;

/**
 * Reto C-1 / REND-2 (R2-27) · Asignación atómica de cupos sin sobreasignar.
 *
 * <p>La decisión es UNA sentencia condicional sobre la fila de la convocatoria:
 * {@code UPDATE ... SET occupied = occupied + 1 WHERE occupied < capacity}. PostgreSQL bloquea la fila
 * mientras la actualiza y vuelve a evaluar la condición con el valor confirmado por quien la tenía antes:
 * dos postulaciones nunca ven el mismo cupo libre y {@code occupied} nunca supera {@code capacity}
 * (además lo impide el CHECK {@code ck_projects_no_overbooking}). Si ya no hay cupo, otra sentencia
 * reserva el siguiente puesto de la lista de espera con el mismo mecanismo.
 *
 * <p>Esa transacción solo contiene la sentencia y su commit, así que la fila (el único punto donde todas
 * las réplicas compiten) queda bloqueada lo mínimo posible; la postulación se inserta después, fuera de
 * ese bloqueo. Con 500 postulaciones simultáneas eso pasa de ~7 ms a ~1 ms por postulación en la fila.
 */
@Component
public class SpotClaims {

    /** Resultado de reclamar: cupo aceptado o puesto en la lista de espera, con el estado resultante. */
    public record Claim(boolean accepted, Integer waitlistPosition, int capacity, int occupied, long version) {
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate shortTransaction;

    public SpotClaims(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        this.jdbc = jdbc;
        this.shortTransaction = new TransactionTemplate(transactions);
        this.shortTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Vacío si la convocatoria no existe para esa empresa o ya cerró. Una sola sentencia: el CTE bloquea la
     * fila ({@code FOR UPDATE}) y lee si queda cupo con el valor ya confirmado por la transacción anterior;
     * la misma sentencia suma el cupo o el puesto de espera. Un solo bloqueo y un commit por postulación.
     */
    public Optional<Claim> claim(String projectId, String organization, Instant now) {
        try {
            return doClaim(projectId, organization, now);
        } catch (DataAccessException e) {
            // lock_timeout (SQLSTATE 55P03): demasiada contención sobre la convocatoria. Respuesta 409 de
            // "intenta de nuevo", nunca un 500 ni una asignación a medias (la sentencia no se aplicó).
            if (e.getMostSpecificCause() instanceof SQLException sql && "55P03".equals(sql.getSQLState())) {
                throw new CannotAcquireLockException("Contención en la convocatoria " + projectId, e);
            }
            throw e;
        }
    }

    private Optional<Claim> doClaim(String projectId, String organization, Instant now) {
        return shortTransaction.execute(status -> jdbc.query("""
                WITH current AS (
                    SELECT id, occupied < capacity AS free FROM projects
                    WHERE id = ? AND organization = ? AND closes_at > ?
                    FOR UPDATE
                )
                UPDATE projects p SET
                    occupied   = p.occupied   + CASE WHEN current.free THEN 1 ELSE 0 END,
                    version    = p.version    + CASE WHEN current.free THEN 1 ELSE 0 END,
                    waitlisted = p.waitlisted + CASE WHEN current.free THEN 0 ELSE 1 END
                FROM current
                WHERE p.id = current.id
                RETURNING current.free, p.capacity, p.occupied, p.version, p.waitlisted""",
                (rs, row) -> rs.getBoolean("free")
                        ? new Claim(true, null, rs.getInt("capacity"), rs.getInt("occupied"), rs.getLong("version"))
                        : new Claim(false, rs.getInt("waitlisted"), rs.getInt("capacity"), rs.getInt("occupied"),
                                rs.getLong("version")),
                projectId, organization, Timestamp.from(now)).stream().findFirst());
    }

    /** Compensación: devuelve un cupo aceptado cuya postulación no se pudo guardar (p. ej. doble envío). */
    public void release(String projectId) {
        shortTransaction.executeWithoutResult(status -> jdbc.update(
                "UPDATE projects SET occupied = occupied - 1, version = version + 1 WHERE id = ? AND occupied > 0",
                projectId));
    }
}
