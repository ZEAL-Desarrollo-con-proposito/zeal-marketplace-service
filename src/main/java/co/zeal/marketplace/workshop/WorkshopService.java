package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.error.BusinessConflictException;
import co.zeal.marketplace.shared.error.ResourceNotFoundException;
import co.zeal.marketplace.shared.security.AuthenticatedUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Talleres próximos del empleado e inscripción concurrente (HU-18, reto C-1). */
@Service
public class WorkshopService {

    private final WorkshopRepository workshops;
    private final EnrollmentRepository enrollments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public WorkshopService(WorkshopRepository workshops, EnrollmentRepository enrollments,
                           ApplicationEventPublisher events, Clock clock) {
        this.workshops = workshops;
        this.enrollments = enrollments;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkshopView> upcoming(AuthenticatedUser employee) {
        return workshops.findUpcoming(employee.organization(), Instant.now(clock)).stream()
                .map(WorkshopView::of)
                .toList();
    }

    /**
     * Con la fila del taller bloqueada se revisa y se toma el cupo en una sola transacción. Quien
     * llega sin cupo recibe 409. Es idempotente: repetir la inscripción devuelve la misma.
     */
    @Transactional
    public EnrollmentOutcome enroll(AuthenticatedUser employee, String workshopId) {
        Instant now = Instant.now(clock);
        Workshop workshop = workshops.findForUpdate(workshopId, employee.organization())
                .orElseThrow(() -> new ResourceNotFoundException("Este taller no existe."));
        var existing = enrollments.findByWorkshopIdAndUserId(workshop.getId(), employee.id());
        if (existing.isPresent()) {
            return new EnrollmentOutcome(EnrollmentView.of(existing.get(), workshop), false);
        }
        if (workshop.hasStartedAt(now)) {
            throw new BusinessConflictException("Este taller ya empezó.");
        }
        if (!workshop.hasFreeSpot()) {
            throw new BusinessConflictException("Este taller ya no tiene cupos.");
        }
        Enrollment enrollment = enrollments.save(Enrollment.enroll(workshop, employee.id(), now));
        enrollments.flush(); // Materializa las versiones que viajan en la respuesta y en los eventos.
        EnrollmentView view = EnrollmentView.of(enrollment, workshop);
        events.publishEvent(WorkshopEvents.WorkshopSpotsChanged.of(workshop));
        events.publishEvent(new WorkshopEvents.EnrollmentCreated(employee.id(), view));
        return new EnrollmentOutcome(view, true);
    }

    /** Inscripciones a talleres que aún no terminan, del más próximo al más lejano. */
    @Transactional(readOnly = true)
    public List<EnrollmentView> myEnrollments(AuthenticatedUser employee) {
        Instant now = Instant.now(clock);
        List<Enrollment> own = enrollments.findByUserId(employee.id());
        Map<String, Workshop> byId = workshops.findAllById(own.stream().map(Enrollment::getWorkshopId).toList())
                .stream()
                .collect(Collectors.toMap(Workshop::getId, Function.identity()));
        return own.stream()
                .filter(enrollment -> byId.containsKey(enrollment.getWorkshopId()))
                .filter(enrollment -> !byId.get(enrollment.getWorkshopId()).hasEndedAt(now))
                .map(enrollment -> EnrollmentView.of(enrollment, byId.get(enrollment.getWorkshopId())))
                .sorted(Comparator.comparing(EnrollmentView::startsAt))
                .toList();
    }
}
