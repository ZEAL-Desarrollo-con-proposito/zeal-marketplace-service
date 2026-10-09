package co.zeal.marketplace.project;

import java.time.Instant;
import java.util.List;

/** Datos ya validados y normalizados de una convocatoria nueva. */
public record ProjectDraft(String title, String area, String cohort, String summary, List<String> description,
                           List<String> requirements, List<String> skills, int capacity, Instant closesAt) {
}
