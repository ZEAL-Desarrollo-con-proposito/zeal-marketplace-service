package co.zeal.marketplace.project;

import java.time.Instant;

/** Convocatoria vista por su administrador: contrato de {@code isAdminProject()} del frontend. */
public record AdminProjectView(String id, String title, String area, String cohort, String summary, int capacity,
                               int occupied, long version, Instant closesAt, Instant publishedAt) {

    static AdminProjectView of(Project project) {
        return new AdminProjectView(project.getId(), project.getTitle(), project.getArea(), project.getCohort(),
                project.getSummary(), project.getCapacity(), project.getOccupied(), project.getVersion(),
                project.getClosesAt(), project.getPublishedAt());
    }
}
