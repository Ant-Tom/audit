package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.consent;
import static ru.siteaudit.siteaudit.support.Pages.form;
import static ru.siteaudit.siteaudit.support.Pages.nameAndPhone;
import static ru.siteaudit.siteaudit.support.Pages.withForms;

class PdConsentPrecheckedRuleTest {

    private final PdConsentPrecheckedRule rule = new PdConsentPrecheckedRule();

    @Test
    void criticalForPrecheckedConsent() {
        var findings = rule.check(List.of(snapshot("prechecked-consent.html", "https://clinic.example/")));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.legalRef()).isEqualTo("pd-consent-form");
            assertThat(f.evidence()).contains("Согласен с политикой конфиденциальности", "name=\"agree\"");
        });
    }

    @Test
    void uncheckedConsentIsFine() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
        assertThat(rule.check(List.of(withForms("https://a.example/", form("x", nameAndPhone(), consent(false)))))).isEmpty();
    }

    @Test
    void precheckedNewsletterIsNotAConsentCheckbox() {
        var newsletter = new CheckboxInfo("news", true, "Хочу получать новости", List.of());
        assertThat(rule.check(List.of(withForms("https://a.example/", form("x", nameAndPhone(), newsletter))))).isEmpty();
    }
}
