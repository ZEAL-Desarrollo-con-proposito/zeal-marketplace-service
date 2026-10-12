package co.zeal.marketplace.shared.text;

import co.zeal.marketplace.shared.error.BusinessRuleViolationException;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Reglas de texto compartidas por los formularios de publicación. */
public final class TextRules {

    private TextRules() {
    }

    /** Devuelve el texto sin espacios sobrantes si su longitud está en {@code [min, max]}. */
    public static String requireLength(String value, int min, int max, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new BusinessRuleViolationException(message);
        }
        return trimmed;
    }

    /** Lista de hasta {@code maxItems} elementos no vacíos de hasta {@code maxItemLength} caracteres. */
    public static List<String> requireList(List<String> values, int maxItems, int maxItemLength, String message) {
        List<String> items = values == null ? List.of() : values;
        if (items.size() > maxItems) {
            throw new BusinessRuleViolationException(message);
        }
        return items.stream().map(item -> requireLength(item, 1, maxItemLength, message)).toList();
    }

    /** Clave para comparar títulos sin importar mayúsculas, tildes ni espacios repetidos. */
    public static String comparisonKey(String value) {
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }
}
