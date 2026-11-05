package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;

class CookieBannerRuleTest {

    private final CookieBannerRule rule = new CookieBannerRule();

    @Test
    void bannerFound() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void bannerOnAnyPageIsEnough() {
        assertThat(rule.check(List.of(
                snapshot("metrika-no-banner.html", "https://s.example/"),
                snapshot("floating-banner.html", "https://s.example/x")))).isEmpty();
    }

    @Test
    void warningWhenNoBanner() {
        assertThat(rule.check(List.of(snapshot("metrika-no-banner.html", "https://s.example/"))))
                .singleElement().satisfies(f -> {
                    assertThat(f.severity()).isEqualTo(Severity.WARNING);
                    assertThat(f.legalRef()).isEqualTo("pd-cookies");
                });
    }
}
