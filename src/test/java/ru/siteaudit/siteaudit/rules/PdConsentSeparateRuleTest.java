package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.form;
import static ru.siteaudit.siteaudit.support.Pages.nameAndPhone;

class PdConsentSeparateRuleTest {

    private final PdConsentSeparateRule rule = new PdConsentSeparateRule();

    @Test
    void linkToSeparateConsentIsFine() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void warnsWhenConsentLinksToPolicy() {
        var findings = rule.check(List.of(snapshot("prechecked-consent.html", "https://clinic.example/")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.legalRef()).isEqualTo("pd-consent-separate");
            assertThat(f.evidence()).contains("«политикой конфиденциальности» (https://clinic.example/politika-konfidencialnosti)");
        });
    }

    @Test
    void warnsWhenConsentLinksToOfferInFormlessForm() {
        assertThat(rule.check(List.of(snapshot("formless-form-bitrix.html", "https://shop.example/")))).hasSize(1);
    }

    @Test
    void linkTextIsTakenFromPageLinks() {
        // Адрес ни о чём не говорит, но текст ссылки — «Политика конфиденциальности»
        var checkbox = new CheckboxInfo("a", false, "Я согласен с политикой конфиденциальности",
                List.of("https://a.example/doc/17"));
        var page = new PageSnapshot("https://a.example/", List.of(), List.of(form("x", nameAndPhone(), checkbox)),
                List.of(new LinkInfo("политикой конфиденциальности", "https://a.example/doc/17")), List.of(), false, "");
        assertThat(rule.check(List.of(page))).hasSize(1);
    }

    @Test
    void unknownLinksAndNoLinksAreNotJudged() {
        var unknown = new CheckboxInfo("a", false, "Согласен на обработку данных", List.of("https://a.example/doc/17"));
        var noLinks = new CheckboxInfo("b", false, "Согласен на обработку персональных данных", List.of());
        var page = new PageSnapshot("https://a.example/", List.of(),
                List.of(form("x", nameAndPhone(), unknown), form("y", nameAndPhone(), noLinks)),
                List.of(), List.of(), false, "");
        assertThat(rule.check(List.of(page))).isEmpty();
    }
}
