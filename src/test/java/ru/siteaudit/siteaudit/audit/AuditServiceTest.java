package ru.siteaudit.siteaudit.audit;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.parser.PageParser;
import ru.siteaudit.siteaudit.rules.Rule;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Pages.page;

class AuditServiceTest {

    private static Rule rule(String id, Finding... findings) {
        return new Rule() {
            public String id() {
                return id;
            }

            public List<Finding> check(List<PageSnapshot> pages) {
                return List.of(findings);
            }
        };
    }

    @Test
    void failingRuleDoesNotBreakOthers() {
        Rule broken = new Rule() {
            public String id() {
                return "broken";
            }

            public List<Finding> check(List<PageSnapshot> pages) {
                throw new IllegalStateException("boom");
            }
        };
        Finding finding = new Finding("ok", Severity.INFO, "t", "d", "u", "e", "r", "rec");
        AuditService service = new AuditService(null, new PageParser(), List.of(broken, rule("ok", finding)));

        List<CrawlWarning> warnings = new ArrayList<>();
        List<Finding> findings = service.runRules(List.of(page("https://a.example/")), warnings);

        assertThat(findings).containsExactly(finding);
        assertThat(warnings).singleElement().satisfies(w -> assertThat(w.message()).contains("broken"));
    }
}
