package co.zeal.marketplace.workshop;

import java.time.Instant;

/** Cuerpo de {@code POST /api/admin/talleres}; las reglas se validan en {@link WorkshopDraftRules}. */
public record NewWorkshopRequest(String title, String description, Instant startsAt, Integer durationMinutes,
                                 Integer capacity) {
}
