package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.id.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Convocatoria interna de una empresa con cupos limitados (HU-14, HU-17). {@code version} sube
 * con cada cambio de cupos: el frontend descarta avisos más viejos que lo que ya muestra.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    private String id;

    @Column(nullable = false)
    private String organization;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String area;

    @Column(nullable = false)
    private String cohort;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> description = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> requirements = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> skills = new ArrayList<>();

    @Column(nullable = false)
    private int capacity;

    @Column(nullable = false)
    private int occupied;

    @Column(name = "closes_at", nullable = false)
    private Instant closesAt;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Version
    private long version;

    protected Project() {
        // JPA
    }

    public static Project publish(String organization, ProjectDraft draft, Instant now) {
        Project project = new Project();
        project.id = Ids.next("proyecto");
        project.organization = organization;
        project.title = draft.title();
        project.area = draft.area();
        project.cohort = draft.cohort();
        project.summary = draft.summary();
        project.description = new ArrayList<>(draft.description());
        project.requirements = new ArrayList<>(draft.requirements());
        project.skills = new ArrayList<>(draft.skills());
        project.capacity = draft.capacity();
        project.occupied = 0;
        project.closesAt = draft.closesAt();
        project.publishedAt = now;
        return project;
    }

    /** Solo para datos de demostración: una convocatoria con cupos ya ocupados. */
    static Project seed(String id, String organization, ProjectDraft draft, int occupied, Instant publishedAt) {
        Project project = publish(organization, draft, publishedAt);
        project.id = id;
        project.occupied = occupied;
        return project;
    }

    public boolean isOpenAt(Instant now) {
        return closesAt.isAfter(now);
    }

    public boolean hasFreeSpot() {
        return occupied < capacity;
    }

    /** Solo se invoca con la fila bloqueada: así dos personas nunca reciben el mismo cupo. */
    public void takeSpot() {
        if (!hasFreeSpot()) {
            throw new IllegalStateException("La convocatoria " + id + " no tiene cupos");
        }
        occupied++;
    }

    public boolean belongsTo(String organization) {
        return this.organization.equals(organization);
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

    public String getArea() {
        return area;
    }

    public String getCohort() {
        return cohort;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getDescription() {
        return List.copyOf(description);
    }

    public List<String> getRequirements() {
        return List.copyOf(requirements);
    }

    public List<String> getSkills() {
        return List.copyOf(skills);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getOccupied() {
        return occupied;
    }

    public Instant getClosesAt() {
        return closesAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getVersion() {
        return version;
    }
}
