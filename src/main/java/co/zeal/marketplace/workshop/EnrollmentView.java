package co.zeal.marketplace.workshop;

import java.time.Instant;

/** Contrato de {@code isEnrollment()} del frontend. */
public record EnrollmentView(String id, String workshopId, String workshopTitle, Instant startsAt,
                             int durationMinutes, Instant createdAt, long version) {

    static EnrollmentView of(Enrollment enrollment, Workshop workshop) {
        return new EnrollmentView(enrollment.getId(), workshop.getId(), workshop.getTitle(), workshop.getStartsAt(),
                workshop.getDurationMinutes(), enrollment.getCreatedAt(), enrollment.getVersion());
    }
}
