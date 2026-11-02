package ru.siteaudit.siteaudit.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.siteaudit.siteaudit.crawler.CrawlResult;
import ru.siteaudit.siteaudit.crawler.SiteCrawler;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.RawPage;
import ru.siteaudit.siteaudit.parser.PageParser;
import ru.siteaudit.siteaudit.rules.Rule;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Оркестрация аудита: обход → разбор → правила → результат.
 * Единая точка входа для CLI, а в будущем — для REST-контроллера и планировщика.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final SiteCrawler crawler;
    private final PageParser parser;
    private final List<Rule> rules;
    private final Clock clock;

    public AuditService(SiteCrawler crawler, PageParser parser, List<Rule> rules) {
        this(crawler, parser, rules, Clock.systemUTC());
    }

    AuditService(SiteCrawler crawler, PageParser parser, List<Rule> rules, Clock clock) {
        this.crawler = crawler;
        this.parser = parser;
        this.rules = List.copyOf(rules);
        this.clock = clock;
    }

    /** Полный аудит сайта. Возвращает {@link Audit}: результат и снимки страниц (для отладки). */
    public Audit audit(String url) {
        Instant started = clock.instant();
        log.info("Аудит {}: правил {}", url, rules.size());
        CrawlResult crawl = crawler.crawl(url);

        List<CrawlWarning> warnings = new ArrayList<>(crawl.warnings());
        List<PageSnapshot> snapshots = new ArrayList<>();
        for (RawPage page : crawl.pages()) {
            try {
                snapshots.add(parser.parse(page));
            } catch (RuntimeException e) {
                log.warn("Не удалось разобрать {}", page.url(), e);
                warnings.add(new CrawlWarning(page.url(), "Не удалось разобрать страницу: " + e.getMessage()));
            }
        }
        if (snapshots.isEmpty()) {
            warnings.add(new CrawlWarning(url, "Не удалось загрузить ни одной страницы — проверки не выполнялись"));
        }
        AuditResult result = new AuditResult(url, started, clock.instant(),
                snapshots.stream().map(PageSnapshot::url).toList(),
                snapshots.isEmpty() ? List.of() : runRules(snapshots, warnings),
                warnings);
        return new Audit(result, List.copyOf(snapshots));
    }

    /** Прогон правил по готовым снимкам. Ошибка одного правила не прерывает остальные. */
    List<Finding> runRules(List<PageSnapshot> snapshots, List<CrawlWarning> warnings) {
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            try {
                findings.addAll(rule.check(snapshots));
            } catch (RuntimeException e) {
                log.error("Правило {} завершилось с ошибкой", rule.id(), e);
                warnings.add(new CrawlWarning(snapshots.getFirst().url(),
                        "Проверка «" + rule.id() + "» не выполнена из-за внутренней ошибки"));
            }
        }
        return findings;
    }

    public List<Rule> rules() {
        return rules;
    }

    /**
     * @param result    результат для отчёта
     * @param snapshots разобранные страницы — не входят в отчёт, нужны для отладки правил
     */
    public record Audit(AuditResult result, List<PageSnapshot> snapshots) {
    }
}
