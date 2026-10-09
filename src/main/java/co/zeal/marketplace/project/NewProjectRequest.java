package co.zeal.marketplace.project;

import java.time.Instant;
import java.util.List;

/** Cuerpo de {@code POST /api/admin/proyectos}; las reglas se validan en {@link ProjectDraftRules}. */
public record NewProjectRequest(String title, String area, String cohort, String summary, List<String> description,
                                List<String> requirements, List<String> skills, Integer capacity, Instant closesAt) {
}
