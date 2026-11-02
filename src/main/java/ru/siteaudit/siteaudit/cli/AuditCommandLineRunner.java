package ru.siteaudit.siteaudit.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.audit.AuditService;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.report.JsonReportWriter;
import ru.siteaudit.siteaudit.report.ReportWriter;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Запуск аудита из командной строки: {@code java -jar site-audit.jar https://example.ru --out=./reports}.
 * Без URL печатает usage и ничего не проверяет — так в приложение потом можно добавить веб-режим.
 */
@Component
public class AuditCommandLineRunner implements ApplicationRunner, ExitCodeGenerator {

    private static final Logger log = LoggerFactory.getLogger(AuditCommandLineRunner.class);

    static final int EXIT_OK = 0;
    static final int EXIT_ERROR = 1;
    static final int EXIT_USAGE = 2;

    static final String USAGE = """
            Автоматическая проверка сайта на признаки нарушений 152-ФЗ и базовых требований к сайтам.

            Использование:
              java -jar site-audit.jar <URL> [--out=<каталог>] [--snapshots]

            Параметры:
              <URL>          адрес сайта, например https://example.ru (без схемы будет добавлен https://)
              --out=<путь>   куда сохранить report.json и report.html (по умолчанию ./reports)
              --snapshots    дополнительно сохранить snapshots.json — разобранные страницы (для отладки правил)

            Настройки обхода (число страниц, таймауты, прокси) — в application.yml, их можно переопределить
            параметрами вида --crawler.max-pages=5.
            """;

    private final AuditService auditService;
    private final List<ReportWriter> writers;
    private final JsonReportWriter json;
    private final PrintStream out;
    private int exitCode = EXIT_OK;

    @Autowired
    public AuditCommandLineRunner(AuditService auditService, List<ReportWriter> writers, JsonReportWriter json) {
        this(auditService, writers, json, System.out);
    }

    AuditCommandLineRunner(AuditService auditService, List<ReportWriter> writers, JsonReportWriter json, PrintStream out) {
        this.auditService = auditService;
        this.writers = writers;
        this.json = json;
        this.out = out;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> positional = args.getNonOptionArgs();
        if (positional.isEmpty() || args.containsOption("help")) {
            out.println(USAGE);
            exitCode = EXIT_USAGE;
            return;
        }
        String url = normalizeUrl(positional.getFirst());
        if (url == null) {
            out.println("Некорректный адрес сайта: " + positional.getFirst());
            out.println();
            out.println(USAGE);
            exitCode = EXIT_USAGE;
            return;
        }
        Path outDir = Path.of(firstOption(args, "out", "./reports"));

        AuditService.Audit audit = auditService.audit(url);
        AuditResult result = audit.result();
        try {
            Files.createDirectories(outDir);
            printSummary(result);
            for (ReportWriter writer : writers) {
                out.println("Сохранено: " + writer.write(result, outDir).toAbsolutePath().normalize());
            }
            if (args.containsOption("snapshots")) {
                Path file = outDir.resolve("snapshots.json");
                Files.writeString(file, json.toJson(audit.snapshots()));
                out.println("Сохранено: " + file.toAbsolutePath().normalize());
            }
        } catch (IOException e) {
            log.error("Не удалось сохранить отчёт в {}", outDir, e);
            exitCode = EXIT_ERROR;
            return;
        }
        exitCode = result.pagesScanned().isEmpty() ? EXIT_ERROR : EXIT_OK;
    }

    private void printSummary(AuditResult result) {
        Map<Severity, Long> counts = result.countsBySeverity();
        out.println();
        out.println("Проверка сайта " + result.targetUrl());
        out.println("Просмотрено страниц: " + result.pagesScanned().size());
        out.printf("Найдено признаков: важно — %d, стоит проверить — %d, к сведению — %d%n",
                counts.get(Severity.CRITICAL), counts.get(Severity.WARNING), counts.get(Severity.INFO));
        for (Finding f : result.findings()) {
            out.printf("  [%s] %s (%s)%n", f.severity(), f.title(), f.ruleId());
        }
        if (!result.warnings().isEmpty()) {
            out.println("Не удалось проверить: " + result.warnings().size() + " (подробности в отчёте)");
        }
        out.println("Отчёт сформирован автоматически и не является юридическим заключением.");
        out.println();
    }

    static String normalizeUrl(String raw) {
        String candidate = raw.trim();
        if (!candidate.toLowerCase(Locale.ROOT).matches("^https?://.*")) {
            if (candidate.contains("://")) {
                return null; // ftp:// и прочее
            }
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            return uri.getHost() == null || !uri.getHost().contains(".") && !uri.getHost().equals("localhost")
                    ? null : candidate;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String firstOption(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return values == null || values.isEmpty() || values.getFirst().isBlank() ? defaultValue : values.getFirst();
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
