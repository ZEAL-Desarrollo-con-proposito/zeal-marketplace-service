package co.zeal.marketplace.project;

import co.zeal.marketplace.shared.error.BusinessRuleViolationException;
import co.zeal.marketplace.shared.text.TextRules;

import java.time.Instant;
import java.util.List;

/** Reglas de negocio de HU-14. Son las mismas que valida el formulario del frontend. */
final class ProjectDraftRules {

    static final int TITLE_MIN = 5;
    static final int TITLE_MAX = 80;
    static final int LABEL_MIN = 2;
    static final int LABEL_MAX = 40;
    static final int SUMMARY_MIN = 10;
    static final int SUMMARY_MAX = 140;
    static final int DESCRIPTION_MIN = 20;
    static final int DESCRIPTION_MAX = 1500;
    static final int MAX_LIST_ITEMS = 8;
    static final int MAX_LIST_ITEM_LENGTH = 100;
    static final int MIN_CAPACITY = 1;
    static final int MAX_CAPACITY = 100;

    private ProjectDraftRules() {
    }

    static ProjectDraft validate(NewProjectRequest request, Instant now) {
        String title = TextRules.requireLength(request.title(), TITLE_MIN, TITLE_MAX,
                "El título debe tener entre " + TITLE_MIN + " y " + TITLE_MAX + " caracteres");
        String labels = "El área y la cohorte deben tener entre " + LABEL_MIN + " y " + LABEL_MAX + " caracteres";
        String area = TextRules.requireLength(request.area(), LABEL_MIN, LABEL_MAX, labels);
        String cohort = TextRules.requireLength(request.cohort(), LABEL_MIN, LABEL_MAX, labels);
        String summary = TextRules.requireLength(request.summary(), SUMMARY_MIN, SUMMARY_MAX,
                "El resumen debe tener entre " + SUMMARY_MIN + " y " + SUMMARY_MAX + " caracteres");
        List<String> description = description(request.description());
        String lists = "Los requisitos y las habilidades admiten hasta " + MAX_LIST_ITEMS + " elementos de "
                + MAX_LIST_ITEM_LENGTH + " caracteres";
        List<String> requirements = TextRules.requireList(request.requirements(), MAX_LIST_ITEMS,
                MAX_LIST_ITEM_LENGTH, lists);
        List<String> skills = TextRules.requireList(request.skills(), MAX_LIST_ITEMS, MAX_LIST_ITEM_LENGTH, lists);
        Integer capacity = request.capacity();
        if (capacity == null || capacity < MIN_CAPACITY || capacity > MAX_CAPACITY) {
            throw new BusinessRuleViolationException("Los cupos deben ser un número entero entre " + MIN_CAPACITY
                    + " y " + MAX_CAPACITY);
        }
        if (request.closesAt() == null || !request.closesAt().isAfter(now)) {
            throw new BusinessRuleViolationException("La fecha límite debe ser posterior a este momento");
        }
        return new ProjectDraft(title, area, cohort, summary, description, requirements, skills, capacity,
                request.closesAt());
    }

    private static List<String> description(List<String> paragraphs) {
        String message = "La descripción debe tener entre " + DESCRIPTION_MIN + " y " + DESCRIPTION_MAX
                + " caracteres";
        if (paragraphs == null || paragraphs.isEmpty()
                || paragraphs.stream().anyMatch(paragraph -> paragraph == null || paragraph.isBlank())) {
            throw new BusinessRuleViolationException(message);
        }
        List<String> trimmed = paragraphs.stream().map(String::trim).toList();
        int length = String.join("", trimmed).length();
        if (length < DESCRIPTION_MIN || length > DESCRIPTION_MAX) {
            throw new BusinessRuleViolationException(message);
        }
        return trimmed;
    }
}
