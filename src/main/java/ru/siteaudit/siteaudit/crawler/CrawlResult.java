package ru.siteaudit.siteaudit.crawler;

import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.RawPage;

import java.util.List;

/** Отрендеренные страницы и проблемы, возникшие при обходе. */
public record CrawlResult(List<RawPage> pages, List<CrawlWarning> warnings) {
    public CrawlResult {
        pages = List.copyOf(pages);
        warnings = List.copyOf(warnings);
    }
}
