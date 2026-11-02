package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.rules.policy.PolicyAnalysis;
import ru.siteaudit.siteaudit.rules.policy.PolicyElement;
import ru.siteaudit.siteaudit.rules.policy.RegexPolicyAnalyzer;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.page;
import static ru.siteaudit.siteaudit.support.Pages.withLinks;

class PdPolicyContentRuleTest {

    private final PdPolicyContentRule rule = new PdPolicyContentRule(new RegexPolicyAnalyzer());

    private static PageSnapshot policyPage(String url, String text) {
        return new PageSnapshot(url, List.of(), List.of(), List.of(), List.of(), false, text);
    }

    @Test
    void fullPolicyHasNoFindings() {
        var home = snapshot("good-site.html", "https://romashka.example/");
        var policy = snapshot("policy-full.html", "https://romashka.example/privacy-policy");
        assertThat(rule.check(List.of(home, policy))).isEmpty();
    }

    @Test
    void warnsAboutMissingElements() {
        var home = withLinks("https://a.example/", new LinkInfo("Политика конфиденциальности", "https://a.example/privacy"));
        var policy = policyPage("https://a.example/privacy",
                "Политика конфиденциальности. Мы бережно относимся к вашим данным. Оператор: ООО «Демо».");

        var findings = rule.check(List.of(home, policy));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.pageUrl()).isEqualTo("https://a.example/privacy");
            assertThat(f.evidence())
                    .contains("ИНН или ОГРН", "цели обработки", "порядок отзыва согласия")
                    .doesNotContain("наименование оператора");
        });
    }

    @Test
    void infoWhenPolicyPageWasNotCrawled() {
        var home = withLinks("https://a.example/", new LinkInfo("Политика", "https://a.example/docs/policy.pdf"));
        assertThat(rule.check(List.of(home))).singleElement()
                .satisfies(f -> assertThat(f.severity()).isEqualTo(Severity.INFO));
    }

    @Test
    void noPolicyLinkNoFinding() {
        assertThat(rule.check(List.of(page("https://a.example/")))).isEmpty();
    }

    @Test
    void policyAnalyzerIsPluggable() {
        PdPolicyContentRule withStub = new PdPolicyContentRule(text ->
                new PolicyAnalysis(Map.of(), List.of(PolicyElement.RETENTION)));
        var home = withLinks("https://a.example/", new LinkInfo("Политика", "https://a.example/p"));
        assertThat(withStub.check(List.of(home, policyPage("https://a.example/p", "любой текст"))))
                .singleElement().satisfies(f -> assertThat(f.evidence()).startsWith("Не найдено: сроки обработки"));
    }
}
