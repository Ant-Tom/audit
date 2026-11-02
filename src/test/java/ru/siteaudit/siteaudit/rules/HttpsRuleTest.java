package ru.siteaudit.siteaudit.rules;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;
import static ru.siteaudit.siteaudit.support.Pages.form;
import static ru.siteaudit.siteaudit.support.Pages.nameAndPhone;
import static ru.siteaudit.siteaudit.support.Pages.withForms;

class HttpsRuleTest {

    private final HttpsRule rule = new HttpsRule();

    @Test
    void httpsSiteIsFine() {
        assertThat(rule.check(List.of(snapshot("good-site.html", "https://romashka.example/")))).isEmpty();
    }

    @Test
    void httpSiteIsCritical_formsOnItAreNotDuplicated() {
        var findings = rule.check(List.of(snapshot("no-policy.html", "http://stroy.example/")));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(f.title()).contains("HTTP");
            assertThat(f.legalRef()).isEqualTo("pd-security");
        });
    }

    @Test
    void httpFormActionOnHttpsPage() {
        var findings = rule.check(List.of(
                withForms("https://a.example/", form("http://a.example/send.php", nameAndPhone())),
                withForms("https://a.example/contacts", form("http://a.example/send.php", nameAndPhone()),
                        form("https://a.example/ok", nameAndPhone()))));
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(f.title()).isEqualTo("Форма отправляет данные без шифрования");
            assertThat(f.evidence()).contains("http://a.example/send.php", "https://a.example/contacts");
        });
    }

    @Test
    void formWithoutFormTagIsIgnored() {
        assertThat(rule.check(List.of(snapshot("formless-form-bitrix.html", "https://shop.example/")))).isEmpty();
    }
}
