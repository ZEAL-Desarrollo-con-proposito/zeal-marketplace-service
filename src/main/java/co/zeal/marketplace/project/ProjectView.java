package co.zeal.marketplace.project;

import java.time.Instant;

/** Tarjeta del catálogo: contrato de {@code isProject()} del frontend. */
public record ProjectView(String id, String title, String area, String cohort, String summary, int capacity,
                          int occupied, long version, Instant closesAt) {

    static ProjectView of(Project project) {
        return new ProjectView(project.getId(), project.getTitle(), project.getArea(), project.getCohort(),
                project.getSummary(), project.getCapacity(), project.getOccupied(), project.getVersion(),
                project.getClosesAt());
    }
}
