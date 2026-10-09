package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.support.Concurrently;
import co.zeal.marketplace.support.IntegrationTest;
import co.zeal.marketplace.support.Users;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Reto C-1 en talleres (HU-18): 200 inscripciones simultáneas a un taller de 20 cupos. */
class WorkshopEnrollmentConcurrencyIT extends IntegrationTest {

    private static final int EMPLOYEES = 200;
    private static final int CAPACITY = 20;

    @Autowired
    AdminWorkshopService adminWorkshopService;
    @Autowired
    WorkshopService workshopService;
    @Autowired
    WorkshopRepository workshops;
    @Autowired
    EnrollmentRepository enrollments;

    @Test
    void neverEnrollsMorePeopleThanCapacity() throws Exception {
        WorkshopView workshop = adminWorkshopService.publish(Users.admin(Users.NEXURA), new NewWorkshopRequest(
                "Taller de carga concurrente", "Taller creado por la prueba de concurrencia C-1.",
                Instant.now().plus(Duration.ofDays(2)), 60, CAPACITY));

        List<Concurrently.Outcome<EnrollmentOutcome>> outcomes = Concurrently.run(EMPLOYEES,
                i -> workshopService.enroll(Users.employee("t-" + i), workshop.id()));

        assertThat(outcomes).filteredOn(Concurrently.Outcome::succeeded).hasSize(CAPACITY);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .hasSize(EMPLOYEES - CAPACITY)
                .allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(BusinessConflictException.class)
                        .hasMessage("Este taller ya no tiene cupos."));
        assertThat(workshops.findById(workshop.id()).orElseThrow().getOccupied()).isEqualTo(CAPACITY);
        assertThat(enrollments.findAll()).filteredOn(e -> e.getWorkshopId().equals(workshop.id())).hasSize(CAPACITY);
    }
}
