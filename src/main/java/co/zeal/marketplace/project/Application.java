package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

/** Postulación de una persona a una convocatoria: tiene un cupo o un puesto en la lista de espera. */
@Entity
@Table(name = "applications")
public class Application {

    @Id
    private String id;

    @Column(name = "project_id", nullable = false)
    private String projectId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status;

    @Column(name = "waitlist_position")
    private Integer waitlistPosition;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Application() {
        // JPA
    }

    /** La postulación que corresponde a un cupo o puesto ya reclamado de forma atómica ({@link SpotClaims}). */
    static Application claimed(String projectId, String userId, SpotClaims.Claim claim, Instant now) {
        return claim.accepted()
                ? create(projectId, userId, ApplicationStatus.ACEPTADA, null, now)
                : create(projectId, userId, ApplicationStatus.EN_ESPERA, claim.waitlistPosition(), now);
    }

    private static Application create(String projectId, String userId, ApplicationStatus status, Integer position,
                                      Instant now) {
        Application application = new Application();
        application.id = Ids.next("postulacion");
        application.projectId = projectId;
        application.userId = userId;
        application.status = status;
        application.waitlistPosition = position;
        application.createdAt = now;
        return application;
    }

    public boolean isAccepted() {
        return status == ApplicationStatus.ACEPTADA;
    }

    public String getId() {
        return id;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getUserId() {
        return userId;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public Integer getWaitlistPosition() {
        return waitlistPosition;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }
}
