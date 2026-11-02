package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.page;
import static ru.siteaudit.siteaudit.support.Pages.withLinks;

class PdPolicyLinkRuleTest {

    private final PdPolicyLinkRule rule = new PdPolicyLinkRule();

    @Test
    void noFindingWhenPolicyLinkPresent() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void policyLinkOnInnerPageIsEnough() {
        var pages = List.of(page("https://a.example/"),
                withLinks("https://a.example/contacts", new LinkInfo("Политика конфиденциальности", "https://a.example/p")));
        assertThat(rule.check(pages)).isEmpty();
    }

    @Test
    void criticalWhenNoPolicyLink() {
        List<Finding> findings = rule.check(List.of(snapshot("no-policy.html", "http://stroy.example/")));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.ruleId()).isEqualTo("pd-policy-link");
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.pageUrl()).isEqualTo("http://stroy.example/");
            assertThat(f.legalRef()).isEqualTo("pd-policy-publication");
            assertThat(f.evidence()).contains("Проверено страниц: 1");
        });
    }

    @Test
    void consentLinkIsNotAPolicy() {
        var pages = List.of(withLinks("https://a.example/",
                new LinkInfo("Согласие на обработку персональных данных", "https://a.example/soglasie")));
        assertThat(rule.check(pages)).singleElement()
                .satisfies(f -> assertThat(f.evidence()).contains("https://a.example/soglasie"));
    }

    @Test
    void noPagesNoFindings() {
        assertThat(rule.check(List.of())).isEmpty();
    }
}
