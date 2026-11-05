package ru.siteaudit.siteaudit.audit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TargetUrlTest {

    @Test
    void addsSchemeAndKeepsHttp() {
        assertThat(TargetUrl.normalize("  example.ru ")).contains("https://example.ru");
        assertThat(TargetUrl.normalize("http://example.ru/a")).contains("http://example.ru/a");
        assertThat(TargetUrl.normalize("HTTPS://Example.ru")).contains("HTTPS://Example.ru");
        assertThat(TargetUrl.normalize("http://localhost:8080/")).contains("http://localhost:8080/");
    }

    @Test
    void rejectsNonSiteInput() {
        assertThat(TargetUrl.normalize(null)).isEmpty();
        assertThat(TargetUrl.normalize("")).isEmpty();
        assertThat(TargetUrl.normalize("   ")).isEmpty();
        assertThat(TargetUrl.normalize("ftp://example.ru")).isEmpty();
        assertThat(TargetUrl.normalize("javascript:alert(1)")).isEmpty();
        assertThat(TargetUrl.normalize("просто текст")).isEmpty();
        assertThat(TargetUrl.normalize("example")).isEmpty();
        assertThat(TargetUrl.normalize("example.ru/" + "a".repeat(TargetUrl.MAX_LENGTH))).isEmpty();
    }
}
