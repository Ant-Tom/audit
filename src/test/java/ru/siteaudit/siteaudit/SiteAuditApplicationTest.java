package ru.siteaudit.siteaudit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SiteAuditApplicationTest {

    @Test
    void webModeOnlyWithFlag() {
        assertThat(SiteAuditApplication.isWebMode(new String[]{"--web"})).isTrue();
        assertThat(SiteAuditApplication.isWebMode(new String[]{"--server.port=9000", "--web=true"})).isTrue();
        assertThat(SiteAuditApplication.isWebMode(new String[]{"https://example.ru", "--out=./r"})).isFalse();
        assertThat(SiteAuditApplication.isWebMode(new String[]{})).isFalse();
    }
}
