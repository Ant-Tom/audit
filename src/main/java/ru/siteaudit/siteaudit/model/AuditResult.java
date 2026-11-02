package ru.siteaudit.siteaudit.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Полный результат аудита одного сайта.
 *
 * @param targetUrl    адрес, который проверяли
 * @param startedAt    время начала
 * @param finishedAt   время окончания
 * @param pagesScanned URL просмотренных страниц
 * @param findings     находки, отсортированные по важности
 * @param warnings     проблемы обхода
 */
public record AuditResult(
        String targetUrl,
        Instant startedAt,
        Instant finishedAt,
        List<String> pagesScanned,
        List<Finding> findings,
        List<CrawlWarning> warnings
) {
    public AuditResult {
        pagesScanned = List.copyOf(pagesScanned);
        findings = findings.stream().sorted(Comparator.comparing(Finding::severity)).toList();
        warnings = List.copyOf(warnings);
    }

    /** Количество находок по каждому уровню (все уровни присутствуют, в том числе с нулём). */
    public Map<Severity, Long> countsBySeverity() {
        Map<Severity, Long> counts = findings.stream()
                .collect(Collectors.groupingBy(Finding::severity, () -> new EnumMap<>(Severity.class),
                        Collectors.counting()));
        for (Severity s : Severity.values()) {
            counts.putIfAbsent(s, 0L);
        }
        return counts;
    }

    public List<Finding> findingsBySeverity(Severity severity) {
        return findings.stream().filter(f -> f.severity() == severity).toList();
    }
}
