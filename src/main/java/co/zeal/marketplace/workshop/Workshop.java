package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;

/** Taller comunitario de una empresa con capacidad máxima (HU-15, HU-18). */
@Entity
@Table(name = "workshops")
public class Workshop {

    @Id
    private String id;

    @Column(nullable = false)
    private String organization;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int occupied;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Version
    private long version;

    protected Workshop() {
        // JPA
    }

    public static Workshop publish(String organization, WorkshopDraft draft, Instant now) {
        Workshop workshop = new Workshop();
        workshop.id = Ids.next("taller");
        workshop.organization = organization;
        workshop.title = draft.title();
        workshop.description = draft.description();
        workshop.startsAt = draft.startsAt();
        workshop.durationMinutes = draft.durationMinutes();
        workshop.capacity = draft.capacity();
        workshop.occupied = 0;
        workshop.publishedAt = now;
        return workshop;
    }

    /** Solo para datos de demostración. */
    static Workshop seed(String id, String organization, WorkshopDraft draft, int occupied, Instant publishedAt) {
        Workshop workshop = publish(organization, draft, publishedAt);
        workshop.id = id;
        workshop.occupied = occupied;
        return workshop;
    }

    public boolean hasStartedAt(Instant now) {
        return !startsAt.isAfter(now);
    }

    public boolean hasEndedAt(Instant now) {
        return !startsAt.plus(Duration.ofMinutes(durationMinutes)).isAfter(now);
    }

    public boolean hasFreeSpot() {
        return occupied < capacity;
    }

    /** Solo se invoca con la fila bloqueada: nunca hay más inscritos que capacidad. */
    public void takeSpot() {
        if (!hasFreeSpot()) {
            throw new IllegalStateException("El taller " + id + " no tiene cupos");
        }
        occupied++;
    }

    public String getId() {
        return id;
    }

    public String getOrganization() {
        return organization;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getOccupied() {
        return occupied;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getVersion() {
        return version;
    }
}
