package co.zeal.marketplace.project;

import jakarta.persistence.*;

@Entity
@Table(name = "projects")
public class Project {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String title;
    private String description;
    @Column(nullable = false)
    private Integer capacity;
    @Column(nullable = false)
    private Integer enrolled;
    private String organizationId;

    public Project() {}
    public Project(Long id, String title, String description, Integer capacity, Integer enrolled, String organizationId) {
        this.id = id; this.title = title; this.description = description;
        this.capacity = capacity; this.enrolled = enrolled; this.organizationId = organizationId;
    }
    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer v) { this.capacity = v; }
    public Integer getEnrolled() { return enrolled; }
    public void setEnrolled(Integer v) { this.enrolled = v; }
    public String getOrganizationId() { return organizationId; }
    public void setOrganizationId(String v) { this.organizationId = v; }

    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private Long id;
        private String title, description, organizationId;
        private Integer capacity, enrolled;
        public Builder id(Long v) { this.id = v; return this; }
        public Builder title(String v) { this.title = v; return this; }
        public Builder description(String v) { this.description = v; return this; }
        public Builder capacity(Integer v) { this.capacity = v; return this; }
        public Builder enrolled(Integer v) { this.enrolled = v; return this; }
        public Builder organizationId(String v) { this.organizationId = v; return this; }
        public Project build() { return new Project(id, title, description, capacity, enrolled, organizationId); }
    }
}
