package co.zeal.marketplace.project;

import co.zeal.marketplace.support.Concurrently;
import co.zeal.marketplace.support.IntegrationTest;
import co.zeal.marketplace.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reto C-1: 500 personas se postulan a la vez a una convocatoria de 10 cupos. Exactamente 10
 * quedan ACEPTADAS, las 490 restantes en lista de espera con puestos 1..490 sin repetir, y la base
 * de datos nunca registra más ocupados que capacidad.
 */
class OverbookingConcurrencyIT extends IntegrationTest {

    private static final int APPLICANTS = 500;
    private static final int CAPACITY = 10;

    @Autowired
    AdminProjectService adminProjectService;
    @Autowired
    ProjectService projectService;
    @Autowired
    ProjectRepository projects;

    @Test
    void neverAssignsMoreSpotsThanCapacity() throws Exception {
        AdminProjectView project = adminProjectService.publish(Users.admin(Users.NEXURA), new NewProjectRequest(
                "Proyecto Alpha de carga", "Plataforma", "Sustentación", "Diez cupos para quinientas personas.",
                List.of("Convocatoria creada por la prueba de concurrencia C-1."), List.of(), List.of(), CAPACITY,
                Instant.now().plus(Duration.ofDays(3))));

        List<Concurrently.Outcome<ApplicationOutcome>> outcomes = Concurrently.run(APPLICANTS,
                i -> projectService.apply(Users.employee("c1-" + i), project.id()));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        List<ApplicationView> results = outcomes.stream().map(outcome -> outcome.value().application()).toList();
        assertThat(results).filteredOn(r -> r.status() == ApplicationStatus.ACEPTADA).hasSize(CAPACITY);
        assertThat(results).filteredOn(r -> r.status() == ApplicationStatus.EN_ESPERA)
                .extracting(ApplicationView::waitlistPosition)
                .containsExactlyInAnyOrderElementsOf(
                        IntStream.rangeClosed(1, APPLICANTS - CAPACITY).boxed().toList());
        Project stored = projects.findById(project.id()).orElseThrow();
        assertThat(stored.getOccupied()).isEqualTo(CAPACITY);
        assertThat(stored.getVersion()).isEqualTo(CAPACITY);
    }

    @Test
    void applyingTwiceReturnsTheSameApplication() {
        AdminProjectView project = adminProjectService.publish(Users.admin(Users.NEXURA), new NewProjectRequest(
                "Proyecto de idempotencia", "Producto", "Pruebas", "Postularse dos veces no ocupa dos cupos.",
                List.of("Convocatoria creada por la prueba de idempotencia."), List.of(), List.of(), 5,
                Instant.now().plus(Duration.ofDays(3))));

        ApplicationOutcome first = projectService.apply(Users.employee("dup"), project.id());
        ApplicationOutcome second = projectService.apply(Users.employee("dup"), project.id());

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.application().id()).isEqualTo(first.application().id());
        assertThat(projects.findById(project.id()).orElseThrow().getOccupied()).isEqualTo(1);
    }

    /** Doble clic desde dos pestañas a la vez: una sola postulación y un solo cupo ocupado (compensación). */
    @Test
    void simultaneousDuplicateSubmissionsTakeASingleSpot() throws Exception {
        AdminProjectView project = adminProjectService.publish(Users.admin(Users.NEXURA), new NewProjectRequest(
                "Proyecto doble envío " + System.nanoTime(), "Producto", "Pruebas", "Diez pestañas, una persona.",
                List.of("Convocatoria creada por la prueba de doble envío."), List.of(), List.of(), 5,
                Instant.now().plus(Duration.ofDays(3))));

        List<Concurrently.Outcome<ApplicationOutcome>> outcomes = Concurrently.run(10,
                i -> projectService.apply(Users.employee("doble-clic"), project.id()));

        assertThat(outcomes).allMatch(Concurrently.Outcome::succeeded);
        assertThat(outcomes).filteredOn(outcome -> outcome.value().created()).hasSize(1);
        assertThat(outcomes).extracting(outcome -> outcome.value().application().id()).containsOnly(
                outcomes.getFirst().value().application().id());
        assertThat(projects.findById(project.id()).orElseThrow().getOccupied()).isEqualTo(1);
    }
}
