package co.zeal.marketplace.workshop;

import co.zeal.marketplace.shared.error.BusinessRuleViolationException;
import co.zeal.marketplace.shared.text.TextRules;

import java.time.Instant;

/** Reglas de negocio de HU-15. Son las mismas que valida el formulario del frontend. */
final class WorkshopDraftRules {

    static final int TITLE_MIN = 5;
    static final int TITLE_MAX = 80;
    static final int DESCRIPTION_MIN = 20;
    static final int DESCRIPTION_MAX = 600;
    static final int MIN_DURATION = 15;
    static final int MAX_DURATION = 240;
    static final int MIN_CAPACITY = 1;
    static final int MAX_CAPACITY = 200;

    private WorkshopDraftRules() {
    }

    static WorkshopDraft validate(NewWorkshopRequest request, Instant now) {
        String title = TextRules.requireLength(request.title(), TITLE_MIN, TITLE_MAX,
                "El título debe tener entre " + TITLE_MIN + " y " + TITLE_MAX + " caracteres");
        String description = TextRules.requireLength(request.description(), DESCRIPTION_MIN, DESCRIPTION_MAX,
                "La descripción debe tener entre " + DESCRIPTION_MIN + " y " + DESCRIPTION_MAX + " caracteres");
        if (request.startsAt() == null || !request.startsAt().isAfter(now)) {
            throw new BusinessRuleViolationException("El taller debe empezar en un momento posterior a este");
        }
        int duration = requireWithin(request.durationMinutes(), MIN_DURATION, MAX_DURATION,
                "La duración debe ser un número entero entre " + MIN_DURATION + " y " + MAX_DURATION + " minutos");
        int capacity = requireWithin(request.capacity(), MIN_CAPACITY, MAX_CAPACITY,
                "La capacidad debe ser un número entero entre " + MIN_CAPACITY + " y " + MAX_CAPACITY);
        return new WorkshopDraft(title, description, request.startsAt(), duration, capacity);
    }

    private static int requireWithin(Integer value, int min, int max, String message) {
        if (value == null || value < min || value > max) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
