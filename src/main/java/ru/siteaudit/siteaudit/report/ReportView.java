package ru.siteaudit.siteaudit.report;

import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Данные для HTML-шаблона: всё уже подготовлено, в шаблоне только вывод. */
public record ReportView(
        String targetUrl,
        String checkedAt,
        List<String> pagesScanned,
        List<Group> groups,
        List<CrawlWarning> warnings,
        long totalFindings
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("d MMMM yyyy, HH:mm (МСК)", Locale.forLanguageTag("ru"))
            .withZone(ZoneId.of("Europe/Moscow"));

    /**
     * @param severity уровень
     * @param label    подпись уровня для владельца бизнеса
     * @param hint     пояснение, что значит уровень
     * @param cssClass класс для оформления
     * @param items    находки уровня
     */
    public record Group(Severity severity, String label, String hint, String cssClass, List<Item> items) {
        public int count() {
            return items.size();
        }
    }

    /** Находка вместе с описанием нормы из legal.yml (норма может отсутствовать). */
    public record Item(Finding finding, LegalProperties.Norm norm) {
    }

    public static ReportView of(AuditResult result, LegalProperties legal) {
        List<Group> groups = Arrays.stream(Severity.values())
                .map(s -> new Group(s, label(s), hint(s), s.name().toLowerCase(Locale.ROOT),
                        result.findingsBySeverity(s).stream()
                                .map(f -> new Item(f, legal.find(f.legalRef()).orElse(null)))
                                .toList()))
                .toList();
        return new ReportView(result.targetUrl(), DATE.format(result.finishedAt()), result.pagesScanned(), groups,
                result.warnings(), result.findings().size());
    }

    private static String label(Severity s) {
        return switch (s) {
            case CRITICAL -> "Важно";
            case WARNING -> "Стоит проверить";
            case INFO -> "К сведению";
        };
    }

    private static String hint(Severity s) {
        return switch (s) {
            case CRITICAL -> "Признаки, которые чаще всего становятся поводом для претензий и штрафов. Рекомендуем разобраться в первую очередь.";
            case WARNING -> "Признаки возможных проблем, которые требуют ручной проверки.";
            case INFO -> "Наблюдения, которые не обязательно означают проблему.";
        };
    }
}
