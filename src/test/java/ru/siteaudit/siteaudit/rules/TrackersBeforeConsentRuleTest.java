package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.support.TestConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.withRequests;

class TrackersBeforeConsentRuleTest {

    private final TrackersBeforeConsentRule rule = new TrackersBeforeConsentRule(TestConfig.trackers());

    @Test
    void metrikaWithoutBannerIsCritical() {
        var page = snapshot("metrika-no-banner.html", "https://store.example/",
                "https://store.example/", "https://mc.yandex.ru/metrika/tag.js", "https://mc.yandex.ru/watch/123?x=1");

        List<Finding> findings = rule.check(List.of(page));

        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.title()).contains("Яндекс Метрика");
            assertThat(f.legalRef()).isEqualTo("pd-trackers-consent");
            assertThat(f.evidence()).contains("https://mc.yandex.ru/metrika/tag.js", "https://mc.yandex.ru/watch/123?x=1");
        });
    }

    @Test
    void oneFindingPerTrackerAcrossPages() {
        var findings = rule.check(List.of(
                withRequests("https://a.example/", "https://mc.yandex.ru/metrika/tag.js",
                        "https://www.googletagmanager.com/gtag/js?id=G-1"),
                withRequests("https://a.example/contacts", "https://mc.yandex.ru/metrika/tag.js",
                        "https://vk.com/rtrg?p=VK-RTRG-1")));

        assertThat(findings).extracting(Finding::title).containsExactly(
                "«Яндекс Метрика» загружается до согласия посетителя",
                "«Google Analytics / Google Tag Manager» загружается до согласия посетителя",
                "«VK Pixel» загружается до согласия посетителя");
        assertThat(findings.getFirst().evidence()).contains("https://a.example/", "https://a.example/contacts");
    }

    @Test
    void nonTrackersAndLookalikesAreIgnored() {
        var findings = rule.check(List.of(withRequests("https://a.example/",
                "https://fonts.googleapis.com/css2?family=Roboto",   // иностранный, но не трекер
                "https://vk.com/widget_community.php",               // VK, но не пиксель
                "https://notmc.yandex.ru.evil.example/tag.js",
                "https://a.example/js/metrika.js")));
        assertThat(findings).isEmpty();
    }
}
