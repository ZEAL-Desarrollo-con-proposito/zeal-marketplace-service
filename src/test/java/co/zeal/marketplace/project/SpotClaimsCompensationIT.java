package co.zeal.marketplace.project;

import co.zeal.marketplace.support.IntegrationTest;
import co.zeal.marketplace.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R2-27 · Compensaciones del reclamo de cupos. Cubre lo que la carrera de dos réplicas (o un fallo) deja a medias:
 * un doble envío que pasa a la vez la comprobación de "ya postulada" en dos réplicas, el lote que falla y la
 * contención sobre la fila de la convocatoria. Cada prueba parte de una convocatoria propia.
 */
class SpotClaimsCompensationIT extends IntegrationTest {

    @Autowired
    AdminProjectService adminProjectService;
    @Autowired
    ProjectService projectService;
    @Autowired
    ApplicationRepository applications;
    @Autowired
    SpotClaims claims;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    PlatformTransactionManager transactions;

    private String publish(int capacity) {
        return adminProjectService.publish(Users.admin(Users.NEXURA), new NewProjectRequest(
                "Compensación " + System.nanoTime(), "Plataforma", "Pruebas", "Convocatoria de la prueba de compensación.",
                List.of("Creada por SpotClaimsCompensationIT."), List.of(), List.of(), capacity,
                Instant.now().plus(Duration.ofDays(3)))).id();
    }

    private int occupied(String projectId) {
        return jdbc.queryForObject("SELECT occupied FROM projects WHERE id = ?", Integer.class, projectId);
    }

    private int waitlisted(String projectId) {
        return jdbc.queryForObject("SELECT waitlisted FROM projects WHERE id = ?", Integer.class, projectId);
    }

    /** Una postulación en lista de espera ya guardada, con su contador, como la dejaría la otra réplica. */
    private Application storeWaitlisted(String projectId, String userId, int position, int capacity) {
        Application stored = applications.saveAndFlush(Application.claimed(projectId, userId,
                new SpotClaims.Claim(false, position, capacity, 0, 0), Instant.now()));
        jdbc.update("UPDATE projects SET waitlisted = waitlisted + 1 WHERE id = ?", projectId);
        return stored;
    }

    @Test
    void releaseReturnsAClaimedSpotAndWaitlistReleaseReturnsAWaitlistPlace() {
        String projectId = publish(1);
        claims.claim(projectId, Users.NEXURA, Instant.now()).orElseThrow(); // cupo
        claims.claim(projectId, Users.NEXURA, Instant.now()).orElseThrow(); // lista de espera
        assertThat(occupied(projectId)).isEqualTo(1);
        assertThat(waitlisted(projectId)).isEqualTo(1);

        claims.release(projectId);
        claims.releaseWaitlist(projectId);

        assertThat(occupied(projectId)).isZero();
        assertThat(waitlisted(projectId)).isZero();
        claims.release(projectId);
        claims.releaseWaitlist(projectId);
        assertThat(occupied(projectId)).as("nunca baja de 0").isZero();
        assertThat(waitlisted(projectId)).as("nunca baja de 0").isZero();
    }

    @Test
    void duplicateWhoseWinnerWasAcceptedGivesItsSpotBack() {
        String projectId = publish(3);
        var person = Users.employee("doble-aceptada");
        projectService.apply(person, projectId); // la ganadora: ACEPTADA, un cupo
        assertThat(occupied(projectId)).isEqualTo(1);

        // La otra réplica no vio la postulación: reclama un cupo, choca con la restricción única y lo devuelve.
        ApplicationOutcome outcome = projectService.claimAndStore(person, projectId, Instant.now());

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.application().status()).isEqualTo(ApplicationStatus.ACEPTADA);
        assertThat(occupied(projectId)).isEqualTo(1);
    }

    @Test
    void duplicateWhoseWinnerWaitedAndWhoseClaimWasAcceptedPromotesTheWinner() {
        String projectId = publish(3);
        var person = Users.employee("doble-promocion");
        storeWaitlisted(projectId, person.id(), 1, 3);

        // Hay cupo libre: la segunda petición lo reclama; la postulación guardada (en espera) se queda con él.
        ApplicationOutcome outcome = projectService.claimAndStore(person, projectId, Instant.now());

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.application().status()).isEqualTo(ApplicationStatus.ACEPTADA);
        assertThat(outcome.application().waitlistPosition()).isNull();
        assertThat(occupied(projectId)).as("ocupa exactamente un cupo, no dos").isEqualTo(1);
        assertThat(waitlisted(projectId)).as("deja la lista de espera").isZero();
    }

    @Test
    void duplicateWhoseClaimWaitedGivesItsWaitlistPlaceBack() {
        String projectId = publish(1);
        projectService.apply(Users.employee("ocupa-el-cupo"), projectId);
        var person = Users.employee("doble-espera");
        storeWaitlisted(projectId, person.id(), 1, 1);
        assertThat(waitlisted(projectId)).isEqualTo(1);

        ApplicationOutcome outcome = projectService.claimAndStore(person, projectId, Instant.now());

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.application().status()).isEqualTo(ApplicationStatus.EN_ESPERA);
        assertThat(waitlisted(projectId)).as("el puesto reclamado de más se devuelve").isEqualTo(1);
        assertThat(occupied(projectId)).isEqualTo(1);
    }

    @Test
    void promotingAnAlreadyAcceptedApplicationReleasesTheExtraSpot() {
        String projectId = publish(3);
        var person = Users.employee("ya-aceptada");
        ApplicationOutcome accepted = projectService.apply(person, projectId);
        claims.claim(projectId, Users.NEXURA, Instant.now()).orElseThrow(); // un cupo reclamado de más
        assertThat(occupied(projectId)).isEqualTo(2);

        claims.promoteStoredToAccepted(accepted.application().id(), projectId);

        assertThat(occupied(projectId)).isEqualTo(1);
    }

    @Test
    void aFailingBatchPropagatesTheFailureToItsClaims() {
        String projectId = publish(2);

        assertThatThrownBy(() -> claims.claim(projectId, Users.NEXURA, null))
                .isInstanceOf(NullPointerException.class);
        // El siguiente reclamo no queda atascado por el fallo anterior.
        assertThat(claims.claim(projectId, Users.NEXURA, Instant.now())).get().extracting(SpotClaims.Claim::accepted)
                .isEqualTo(true);
    }

    @Test
    void aClosedOrUnknownProjectHasNoClaim() {
        assertThat(claims.claim("no-existe-" + System.nanoTime(), Users.NEXURA, Instant.now())).isEmpty();
    }

    @Test
    void contentionOnTheProjectRowBecomesAConflictInsteadOfAServerError() throws Exception {
        String projectId = publish(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Otra transacción retiene la fila de la convocatoria más tiempo que el lock_timeout.
            Future<?> holder = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                jdbc.queryForList("SELECT id FROM projects WHERE id = ? FOR UPDATE", projectId);
                locked.countDown();
                try {
                    finish.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> claims.claim(projectId, Users.NEXURA, Instant.now()))
                    .isInstanceOf(CannotAcquireLockException.class);

            finish.countDown();
            holder.get(10, TimeUnit.SECONDS);
        } finally {
            finish.countDown();
            executor.shutdownNow();
        }
        assertThat(occupied(projectId)).as("el reclamo rechazado no ocupó nada").isZero();
    }
}
