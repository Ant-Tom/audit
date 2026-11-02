package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.FieldInfo;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.consent;
import static ru.siteaudit.siteaudit.support.Pages.form;
import static ru.siteaudit.siteaudit.support.Pages.nameAndPhone;
import static ru.siteaudit.siteaudit.support.Pages.withForms;

class PdFormConsentRuleTest {

    private final PdFormConsentRule rule = new PdFormConsentRule();

    @Test
    void formWithConsentCheckboxIsFine() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void criticalForPersonalDataFormWithoutCheckbox() {
        List<Finding> findings = rule.check(List.of(snapshot("no-policy.html", "http://stroy.example/")));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.legalRef()).isEqualTo("pd-consent-required");
            assertThat(f.evidence()).contains("«ФИО» (имя)", "«Телефон» (телефон)", "POST http://stroy.example/order.php");
        });
    }

    @Test
    void unrelatedCheckboxIsNotConsent() {
        var newsletter = new CheckboxInfo("news", false, "Получать новости и акции", List.of());
        var findings = rule.check(List.of(withForms("https://a.example/", form("https://a.example/s", nameAndPhone(), newsletter))));
        assertThat(findings).hasSize(1);
    }

    @Test
    void formsWithoutPersonalDataAreIgnored() {
        var search = form("https://a.example/search", List.of(new FieldInfo("q", "search", "Поиск")));
        assertThat(rule.check(List.of(withForms("https://a.example/", search)))).isEmpty();
    }

    @Test
    void loginFormIsIgnored() {
        var login = form("https://a.example/login", List.of(
                new FieldInfo("email", FieldInfo.TYPE_EMAIL, "Email"), new FieldInfo("pass", "password", "Пароль")));
        assertThat(rule.check(List.of(withForms("https://a.example/", login)))).isEmpty();
    }

    @Test
    void sameFormOnManyPagesGivesOneFinding() {
        var callback = form("https://a.example/cb", nameAndPhone());
        var findings = rule.check(List.of(
                withForms("https://a.example/", callback),
                withForms("https://a.example/about", callback),
                withForms("https://a.example/contacts", callback, form("https://a.example/order", nameAndPhone(), consent(false)))));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.pageUrl()).isEqualTo("https://a.example/");
            assertThat(f.evidence()).contains("https://a.example/about", "https://a.example/contacts");
        });
    }

    @Test
    void formlessFormWithOfferCheckboxCountsAsHavingCheckbox() {
        // «Я принимаю условия оферты» — чекбокс есть; то, что он ведёт на оферту, проверяет pd-consent-separate
        assertThat(rule.check(List.of(snapshot("formless-form-bitrix.html", "https://shop.example/")))).isEmpty();
    }
}
