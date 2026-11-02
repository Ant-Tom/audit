package ru.siteaudit.siteaudit.report;

import ru.siteaudit.siteaudit.model.AuditResult;

import java.io.IOException;
import java.nio.file.Path;

/** Формат отчёта. Новые форматы (PDF и т.п.) — новые реализации-бины. */
public interface ReportWriter {

    /** Имя файла, например {@code report.json}. */
    String fileName();

    /** Записывает отчёт в каталог и возвращает путь к файлу. */
    Path write(AuditResult result, Path outDir) throws IOException;
}
