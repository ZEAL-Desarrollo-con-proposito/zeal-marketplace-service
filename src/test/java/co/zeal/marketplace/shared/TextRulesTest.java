package co.zeal.marketplace.shared;

import co.zeal.marketplace.shared.error.BusinessRuleViolationException;
import co.zeal.marketplace.shared.text.TextRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextRulesTest {

    @Test
    void titlesCompareIgnoringCaseAccentsAndSpacing() {
        assertThat(TextRules.comparisonKey("  Mentoría   Inversa ")).isEqualTo(TextRules.comparisonKey("mentoria inversa"));
    }

    @Test
    void lengthIsCheckedOnTheTrimmedText() {
        assertThat(TextRules.requireLength("  Hola mundo  ", 5, 20, "x")).isEqualTo("Hola mundo");
        assertThatThrownBy(() -> TextRules.requireLength("  Hey  ", 5, 20, "corto"))
                .isInstanceOf(BusinessRuleViolationException.class).hasMessage("corto");
    }

    @Test
    void listsRejectBlankItemsAndTooManyItems() {
        assertThatThrownBy(() -> TextRules.requireList(List.of("ok", " "), 8, 100, "lista"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> TextRules.requireList(List.of("1", "2", "3"), 2, 100, "lista"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
