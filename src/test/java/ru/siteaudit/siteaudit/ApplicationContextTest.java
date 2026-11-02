package ru.siteaudit.siteaudit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.siteaudit.siteaudit.audit.AuditService;
import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.legal.TrackerProperties;
import ru.siteaudit.siteaudit.report.ReportWriter;
import ru.siteaudit.siteaudit.rules.Rule;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Контекст поднимается без запуска браузера (бины Playwright ленивые), все правила зарегистрированы. */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    AuditService auditService;
    @Autowired
    List<ReportWriter> writers;
    @Autowired
    LegalProperties legal;
    @Autowired
    TrackerProperties trackers;

    @Test
    void allRulesAndWritersRegistered() {
        assertThat(auditService.rules()).extracting(Rule::id).containsExactlyInAnyOrder(
                "pd-policy-link", "pd-policy-content", "pd-form-consent", "pd-consent-prechecked",
                "pd-consent-separate", "cookie-banner", "trackers-before-consent", "foreign-services",
                "https", "ad-erid");
        assertThat(writers).extracting(ReportWriter::fileName).containsExactlyInAnyOrder("report.json", "report.html");
        assertThat(legal.norms()).isNotEmpty();
        assertThat(trackers.services()).isNotEmpty();
        assertThat(trackers.adLinks().domains()).isNotEmpty();
    }
}
