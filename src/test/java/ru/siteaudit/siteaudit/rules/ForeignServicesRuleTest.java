package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.support.TestConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Pages.withRequests;

class ForeignServicesRuleTest {

    private final ForeignServicesRule rule = new ForeignServicesRule(TestConfig.trackers());

    @Test
    void reportsEachForeignServiceOnce() {
        var findings = rule.check(List.of(
                withRequests("https://a.example/",
                        "https://fonts.googleapis.com/css2?family=Roboto",
                        "https://fonts.gstatic.com/s/roboto/v1.woff2",
                        "https://mc.yandex.ru/metrika/tag.js"),
                withRequests("https://a.example/contacts",
                        "https://www.google.com/recaptcha/api.js",
                        "https://www.google-analytics.com/g/collect?v=2")));

        assertThat(findings).extracting(Finding::title).containsExactly(
                "Используется иностранный сервис «Google Fonts»",
                "Используется иностранный сервис «Google reCAPTCHA»",
                "Используется иностранный сервис «Google Analytics / Google Tag Manager»");
        assertThat(findings).allSatisfy(f -> {
            assertThat(f.severity()).isEqualTo(Severity.WARNING);
            assertThat(f.legalRef()).isEqualTo("pd-cross-border");
        });
        assertThat(findings.getFirst().evidence()).contains("fonts.gstatic.com");
    }

    @Test
    void russianServicesAreNotForeign() {
        assertThat(rule.check(List.of(withRequests("https://a.example/",
                "https://mc.yandex.ru/metrika/tag.js", "https://top-fwz1.mail.ru/js/code.js")))).isEmpty();
    }
}
