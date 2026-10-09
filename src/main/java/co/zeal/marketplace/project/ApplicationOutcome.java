package co.zeal.marketplace.project;

/** Resultado de postularse: {@code created} es falso si la persona ya estaba postulada (idempotencia). */
public record ApplicationOutcome(ApplicationView application, boolean created) {
}
