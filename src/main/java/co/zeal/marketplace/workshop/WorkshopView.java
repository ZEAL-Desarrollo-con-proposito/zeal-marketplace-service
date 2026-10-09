package co.zeal.marketplace.workshop;

import java.time.Instant;

/** Contrato de {@code isWorkshop()} del frontend. */
public record WorkshopView(String id, String title, String description, Instant startsAt, int durationMinutes,
                           int capacity, int occupied, long version, Instant publishedAt) {

    static WorkshopView of(Workshop workshop) {
        return new WorkshopView(workshop.getId(), workshop.getTitle(), workshop.getDescription(),
                workshop.getStartsAt(), workshop.getDurationMinutes(), workshop.getCapacity(),
                workshop.getOccupied(), workshop.getVersion(), workshop.getPublishedAt());
    }
}
