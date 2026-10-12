package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Inscripción de una persona a un taller. Solo existe si obtuvo cupo. */
@Entity
@Table(name = "enrollments")
public class Enrollment {

    @Id
    private String id;

    @Column(name = "workshop_id", nullable = false)
    private String workshopId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Enrollment() {
        // JPA
    }

    static Enrollment enroll(Workshop workshop, String userId, Instant now) {
        workshop.takeSpot();
        Enrollment enrollment = new Enrollment();
        enrollment.id = Ids.next("inscripcion");
        enrollment.workshopId = workshop.getId();
        enrollment.userId = userId;
        enrollment.createdAt = now;
        return enrollment;
    }

    public String getId() {
        return id;
    }

    public String getWorkshopId() {
        return workshopId;
    }

    public String getUserId() {
        return userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
