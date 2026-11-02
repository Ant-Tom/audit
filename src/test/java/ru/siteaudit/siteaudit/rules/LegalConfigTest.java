package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.support.TestConfig;

import static org.assertj.core.api.Assertions.assertThat;

/** legal.yml и trackers.yml читаются, и каждая норма, на которую ссылаются правила, описана. */
class LegalConfigTest {

    @Test
    void allReferencedNormsExist() {
        LegalProperties legal = TestConfig.legal();
        assertThat(legal.norms()).containsKeys(
                "pd-policy-publication", "pd-policy-content", "pd-consent-required", "pd-consent-form",
                "pd-consent-separate", "pd-cookies", "pd-trackers-consent", "pd-cross-border",
                "pd-security", "ad-marking");
        assertThat(legal.norms().values()).allSatisfy(n -> {
            assertThat(n.title()).isNotBlank();
            assertThat(n.article()).isNotBlank();
            assertThat(n.summary()).isNotBlank();
        });
    }

    @Test
    void trackersLoaded() {
        var trackers = TestConfig.trackers();
        assertThat(trackers.services()).isNotEmpty();
        assertThat(trackers.match("https://mc.yandex.ru/watch/1")).hasValueSatisfying(s -> {
            assertThat(s.tracker()).isTrue();
            assertThat(s.foreign()).isFalse();
        });
        assertThat(trackers.match("https://www.google-analytics.com/g/collect")).hasValueSatisfying(s -> {
            assertThat(s.tracker()).isTrue();
            assertThat(s.foreign()).isTrue();
        });
    }
}
