package co.zeal.marketplace.project;

import java.time.Instant;
import java.util.List;

/** Detalle de la convocatoria: contrato de {@code isProjectDetail()} del frontend. */
public record ProjectDetailView(String id, String title, String area, String cohort, String summary, int capacity,
                                int occupied, long version, Instant closesAt, List<String> description,
                                List<String> requirements, List<String> skills) {

    static ProjectDetailView of(Project project) {
        return new ProjectDetailView(project.getId(), project.getTitle(), project.getArea(), project.getCohort(),
                project.getSummary(), project.getCapacity(), project.getOccupied(), project.getVersion(),
                project.getClosesAt(), project.getDescription(), project.getRequirements(), project.getSkills());
    }
}
