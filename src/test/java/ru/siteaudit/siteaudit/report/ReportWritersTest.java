package ru.siteaudit.siteaudit.report;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.support.TestConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportWritersTest {

    private static final Instant T = Instant.parse("2026-11-02T09:00:00Z");

    private static AuditResult result() {
        return new AuditResult("https://romashka.example/", T, T.plusSeconds(30),
                List.of("https://romashka.example/", "https://romashka.example/contacts"),
                List.of(
                        new Finding("ad-erid", Severity.INFO, "Ссылки без erid", "desc-info", "https://romashka.example/",
                                "Ссылки: https://ad.admitad.com/g/1/", "ad-marking", "rec-info"),
                        new Finding("pd-policy-link", Severity.CRITICAL, "Нет политики <script>", "desc-crit",
                                "https://romashka.example/", "Проверено страниц: 2", "pd-policy-publication", "rec-crit"),
                        new Finding("custom", Severity.WARNING, "Без нормы", "d", "https://romashka.example/", "e",
                                "no-such-norm", "r")),
                List.of(new CrawlWarning("https://romashka.example/broken", "Страница не загрузилась: 500")));
    }

    @Test
    void jsonContainsFullResult(@TempDir Path dir) throws Exception {
        Path file = new JsonReportWriter().write(result(), dir);

        JsonNode root = JsonReportWriter.createMapper().readTree(file.toFile());
        assertThat(root.get("targetUrl").asText()).isEqualTo("https://romashka.example/");
        assertThat(root.get("startedAt").asText()).isEqualTo("2026-11-02T09:00:00Z");
        assertThat(root.get("findings")).hasSize(3);
        assertThat(root.get("findings").get(0).get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(root.get("findings").get(0).get("legalRef").asText()).isEqualTo("pd-policy-publication");
        assertThat(root.get("warnings").get(0).get("message").asText()).contains("500");
    }

    @Test
    void htmlIsSelfContainedAndGroupedBySeverity(@TempDir Path dir) throws Exception {
        Path file = new HtmlReportWriter(TestConfig.legal()).write(result(), dir);
        String html = Files.readString(file);

        assertThat(html).contains("<style>").doesNotContain("<link").doesNotContain("<script src");
        assertThat(html).contains("https://romashka.example/", "2 ноября 2026, 12:00 (МСК)");
        // Порядок групп: важно → стоит проверить → к сведению
        assertThat(html.indexOf("desc-crit")).isLessThan(html.indexOf("desc-info"));
        // Норма из legal.yml и пометка о непроверенных сведениях
        assertThat(html).contains("ч. 2 ст. 18.1", "Сведения о норме и штрафе требуют уточнения");
        // Экранирование
        assertThat(html).contains("Нет политики &lt;script&gt;").doesNotContain("Нет политики <script>");
        assertThat(html).contains("не является юридическим заключением");
        assertThat(html).contains("Страница не загрузилась: 500");
    }

    @Test
    void verifiedNormShowsDate() {
        var norm = new LegalProperties.Norm("T", "ст. 1", "S", "КоАП", "100 руб.", LocalDate.of(2026, 10, 1));
        String html = new HtmlReportWriter(new LegalProperties(Map.of("pd-policy-publication", norm))).render(result());
        assertThat(html).contains("Сведения о норме проверены 01.10.2026", "Возможный штраф для организации: 100 руб.");
    }

    @Test
    void reportNeverClaimsCompliance() {
        var empty = new AuditResult("https://a.example/", T, T, List.of("https://a.example/"), List.of(), List.of());
        String html = new HtmlReportWriter(TestConfig.legal()).render(empty);
        assertThat(html).contains("признаков возможных нарушений не обнаружено", "Это не означает, что сайт полностью");
        assertThat(html.toLowerCase()).doesNotContain("сайт соответствует", "нарушили закон");
    }
}
