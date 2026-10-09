package co.zeal.marketplace.workshop;

import java.time.Instant;

/** Datos ya validados y normalizados de un taller nuevo. */
public record WorkshopDraft(String title, String description, Instant startsAt, int durationMinutes, int capacity) {
}
