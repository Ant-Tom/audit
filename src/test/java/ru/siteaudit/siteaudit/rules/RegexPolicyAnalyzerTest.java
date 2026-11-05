package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.rules.policy.PolicyAnalysis;
import ru.siteaudit.siteaudit.rules.policy.PolicyElement;
import ru.siteaudit.siteaudit.rules.policy.RegexPolicyAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;

class RegexPolicyAnalyzerTest {

    private final RegexPolicyAnalyzer analyzer = new RegexPolicyAnalyzer();

    @Test
    void findsAllElementsInFullPolicy() {
        PolicyAnalysis analysis = analyzer.analyze(snapshot("policy-full.html", "https://r.example/p").visibleText());
        assertThat(analysis.missing()).isEmpty();
        assertThat(analysis.found().get(PolicyElement.OPERATOR_ID)).contains("7701234567");
    }

    @Test
    void alternativeWordings() {
        PolicyAnalysis analysis = analyzer.analyze("""
                Индивидуальный предприниматель Иванов И.И., ОГРНИП: 304770000000000.
                Персональные данные используются для целей обработки заказов.
                Обрабатываются следующие данные: имя, телефон.
                Данные хранятся в течение 1 года.
                Пользователь вправе требовать уточнения своих данных.
                Пользователь может отозвать своё согласие, направив письмо.""");
        assertThat(analysis.missing()).isEmpty();
    }

    @Test
    void emptyTextMissesEverything() {
        assertThat(analyzer.analyze("").missing()).containsExactly(PolicyElement.values());
        assertThat(analyzer.analyze(null).missing()).hasSize(PolicyElement.values().length);
    }

    @Test
    void wordInsideAnotherWordIsNotAnOperator() {
        // «ип» внутри слова «типичный» и «ао» внутри «Макао» не должны считаться формой собственности
        assertThat(analyzer.analyze("Типичный текст про Макао").found()).doesNotContainKey(PolicyElement.OPERATOR_NAME);
    }
}
