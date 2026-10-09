package co.zeal.marketplace.workshop;

/** Resultado de inscribirse: {@code created} es falso si la persona ya estaba inscrita (idempotencia). */
public record EnrollmentOutcome(EnrollmentView enrollment, boolean created) {
}
