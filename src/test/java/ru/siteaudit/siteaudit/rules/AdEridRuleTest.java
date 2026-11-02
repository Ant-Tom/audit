package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.support.TestConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;

class AdEridRuleTest {

    private final AdEridRule rule = new AdEridRule(TestConfig.trackers());

    private static PageSnapshot withExternal(String url, LinkInfo... links) {
        return new PageSnapshot(url, List.of(), List.of(), List.of(), List.of(links), false, "");
    }

    @Test
    void ordinaryExternalLinksAreIgnored() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void infoForAdLinksWithoutErid() {
        var findings = rule.check(List.of(
                withExternal("https://a.example/",
                        new LinkInfo("Купить", "https://ad.admitad.com/g/abc123/"),
                        new LinkInfo("Партнёр", "https://partner.example/?utm_source=site&utm_medium=cpa"),
                        new LinkInfo("Промаркировано", "https://ad.admitad.com/g/xyz/?erid=2VtzqvXyZ1")),
                withExternal("https://a.example/blog",
                        new LinkInfo("Купить", "https://ad.admitad.com/g/abc123/"))));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.INFO);
            assertThat(f.legalRef()).isEqualTo("ad-marking");
            assertThat(f.evidence())
                    .contains("https://ad.admitad.com/g/abc123/", "utm_medium=cpa", "https://a.example/blog")
                    .doesNotContain("erid=2VtzqvXyZ1");
        });
    }
}
