package ru.siteaudit.siteaudit.crawler;

import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.RequestOptions;
import com.microsoft.playwright.options.WaitUntilState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.CrawlWarning;
import ru.siteaudit.siteaudit.model.RawPage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Обходит сайт в headless-браузере: главная, юридические документы и до {@code maxPages} внутренних
 * страниц. Только рендерит страницы и собирает сетевые запросы — разбор DOM выполняет PageParser.
 *
 * <p>На каждый аудит создаётся новый {@link BrowserContext}: cookie и хранилища разных проверок не смешиваются.
 * Обработчик запросов подключается до навигации, по странице никто не кликает — значит, все собранные
 * запросы сделаны до какого-либо согласия пользователя.
 */
@Component
public class SiteCrawler {

    private static final Logger log = LoggerFactory.getLogger(SiteCrawler.class);

    private static final Duration MAX_CRAWL_DELAY = Duration.ofSeconds(10);
    private static final int MAX_NESTED_SITEMAPS = 3;

    /**
     * Переносит в атрибуты то, чего не видно в HTML-разметке, но нужно парсеру:
     * <ul>
     *   <li>текущее состояние чекбоксов (свойство {@code checked}, выставленное скриптом, не попадает в атрибут);</li>
     *   <li>вычисленное позиционирование fixed/sticky видимых элементов — признак плавающего баннера.</li>
     * </ul>
     * Это не взаимодействие с сайтом: запросы уже собраны, скрипт меняет только локальную копию DOM.
     */
    static final String PREPARE_DOM_SCRIPT = """
            () => {
              for (const c of document.querySelectorAll('input[type=checkbox], input[type=radio]')) {
                if (c.checked) c.setAttribute('checked', ''); else c.removeAttribute('checked');
              }
              if (!document.body) return;
              for (const el of document.body.querySelectorAll('*')) {
                const pos = getComputedStyle(el).position;
                if ((pos === 'fixed' || pos === 'sticky') && el.getClientRects().length > 0) {
                  el.setAttribute('data-sa-position', pos);
                }
              }
            }
            """;

    private final ObjectProvider<Browser> browserProvider;
    private final CrawlerProperties properties;

    public SiteCrawler(ObjectProvider<Browser> browserProvider, CrawlerProperties properties) {
        this.browserProvider = browserProvider;
        this.properties = properties;
    }

    /**
     * Синхронизирован, потому что объекты Playwright не потокобезопасны (см. {@link BrowserConfig}).
     */
    public synchronized CrawlResult crawl(String startUrl) {
        Browser browser = browserProvider.getObject();
        Browser.NewContextOptions options = new Browser.NewContextOptions()
                .setUserAgent(properties.userAgent())
                .setLocale("ru-RU")
                .setTimezoneId("Europe/Moscow")
                .setViewportSize(1366, 900);
        try (BrowserContext context = browser.newContext(options)) {
            context.setDefaultNavigationTimeout(properties.navigationTimeout().toMillis());
            return new Session(context).run(startUrl);
        }
    }

    private record Candidate(String url, int score, long seq) {
        boolean legal() {
            return score == LinkPrioritizer.LEGAL;
        }
    }

    /** Состояние одного обхода. */
    private final class Session {
        private final BrowserContext context;
        private final List<RawPage> pages = new ArrayList<>();
        private final List<CrawlWarning> warnings = new ArrayList<>();
        private final Set<String> visited = new HashSet<>();
        private final Map<String, Integer> bestScore = new HashMap<>();
        private final PriorityQueue<Candidate> queue = new PriorityQueue<>(
                Comparator.comparingInt(Candidate::score).reversed().thenComparingLong(Candidate::seq));
        private final Set<String> robotsBlocked = new LinkedHashSet<>();
        private long seq;
        private String siteHost = "";
        private RobotsTxt robots = RobotsTxt.allowAll();
        private Duration delay = properties.delayBetweenRequests();

        Session(BrowserContext context) {
            this.context = context;
        }

        CrawlResult run(String startUrl) {
            Optional<String> start = UrlUtils.normalize(startUrl);
            if (start.isEmpty()) {
                warnings.add(new CrawlWarning(startUrl, "Некорректный адрес сайта"));
                return result();
            }
            String startNormalized = start.get();
            siteHost = UrlUtils.siteHost(startNormalized);
            loadRobots(startNormalized);
            if (!robotsAllowed(startNormalized)) {
                warnings.add(new CrawlWarning(startNormalized,
                        "Файл robots.txt запрещает обход сайта нашему роботу; проверка не выполнялась"));
                return result();
            }

            Optional<RawPage> home = visit(startNormalized);
            if (home.isEmpty()) {
                return result();
            }
            // Главная могла отредиректить на другой хост (www, https) — дальше считаем «своим» его
            siteHost = UrlUtils.siteHost(home.get().url());
            addPage(home.get());

            if (properties.readSitemap()) {
                loadSitemap(home.get().url());
            }

            int regular = 0;
            int legal = 0;
            while (!queue.isEmpty() && (regular < properties.maxPages() || legal < properties.maxLegalPages())) {
                Candidate next = queue.poll();
                if (visited.contains(next.url())) {
                    continue;
                }
                if (next.legal() ? legal >= properties.maxLegalPages() : regular >= properties.maxPages()) {
                    continue;
                }
                if (!robotsAllowed(next.url())) {
                    visited.add(next.url());
                    robotsBlocked.add(next.url());
                    continue;
                }
                if (next.legal()) {
                    legal++;
                } else {
                    regular++;
                }
                pause();
                visit(next.url()).ifPresent(this::addPage);
            }
            if (!robotsBlocked.isEmpty()) {
                warnings.add(new CrawlWarning(home.get().url(), "Не проверено страниц из-за запрета в robots.txt: "
                        + robotsBlocked.size() + " (например, " + robotsBlocked.iterator().next() + ")"));
            }
            return result();
        }

        private CrawlResult result() {
            return new CrawlResult(pages, warnings);
        }

        private void addPage(RawPage page) {
            pages.add(page);
            org.jsoup.nodes.Document doc = Jsoup.parse(page.renderedHtml(), page.url());
            for (Element a : doc.select("a[href]")) {
                String text = a.text();
                UrlUtils.normalize(a.absUrl("href")).ifPresent(url -> enqueue(url, text));
            }
        }

        private void enqueue(String url, String anchorText) {
            if (!UrlUtils.sameSite(url, siteHost) || UrlUtils.looksLikeFile(url) || visited.contains(url)) {
                return;
            }
            int score = LinkPrioritizer.score(anchorText, url);
            Integer known = bestScore.get(url);
            if (known != null && known >= score) {
                return;
            }
            bestScore.put(url, score);
            queue.add(new Candidate(url, score, seq++));
        }

        private Optional<RawPage> visit(String url) {
            visited.add(url);
            String lastError = "";
            for (int attempt = 0; attempt <= properties.retries(); attempt++) {
                if (attempt > 0) {
                    log.info("Повтор загрузки {}", url);
                    pause();
                }
                Page page = context.newPage();
                List<String> requests = Collections.synchronizedList(new ArrayList<>());
                // Подписка ДО навигации — иначе потеряем ранние запросы счётчиков
                page.onRequest(request -> {
                    String requestUrl = request.url();
                    if (requestUrl.startsWith("http")) {
                        requests.add(requestUrl);
                    }
                });
                try {
                    log.info("Загружаю {}", url);
                    Response response = page.navigate(url, new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.LOAD)
                            .setTimeout(properties.navigationTimeout().toMillis()));
                    if (response != null && response.status() >= 400) {
                        lastError = "сервер ответил кодом " + response.status();
                        if (response.status() < 500) {
                            break; // 4xx повторять бессмысленно
                        }
                        continue;
                    }
                    String contentType = response == null ? "" : response.headerValue("content-type");
                    if (contentType != null && !contentType.isBlank() && !contentType.contains("html")) {
                        log.info("Пропускаю {}: не HTML ({})", url, contentType);
                        return Optional.empty();
                    }
                    try {
                        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions()
                                .setTimeout(properties.networkIdleTimeout().toMillis()));
                    } catch (TimeoutError e) {
                        // На страницах с постоянными запросами (чаты, вебсокеты) тишины в сети не бывает — это норма
                        log.debug("Не дождались тишины в сети на {}", url);
                    }
                    page.evaluate(PREPARE_DOM_SCRIPT);
                    String finalUrl = UrlUtils.normalize(page.url()).orElse(page.url());
                    String html = page.content();
                    visited.add(finalUrl);
                    if (!pages.isEmpty() && !UrlUtils.sameSite(finalUrl, siteHost)) {
                        log.info("Пропускаю {}: перенаправление на другой сайт {}", url, finalUrl);
                        return Optional.empty();
                    }
                    if (pages.stream().anyMatch(p -> p.url().equals(finalUrl))) {
                        return Optional.empty(); // редирект на уже просмотренную страницу
                    }
                    return Optional.of(new RawPage(finalUrl, html, List.copyOf(requests)));
                } catch (PlaywrightException e) {
                    lastError = firstLine(e.getMessage());
                    log.warn("Ошибка загрузки {} (попытка {}): {}", url, attempt + 1, lastError);
                } finally {
                    page.close();
                }
            }
            warnings.add(new CrawlWarning(url, "Страница не загрузилась: " + lastError));
            return Optional.empty();
        }

        private void loadRobots(String startUrl) {
            if (!properties.respectRobotsTxt()) {
                return;
            }
            fetchText(UrlUtils.origin(startUrl) + "/robots.txt").ifPresent(body -> {
                robots = RobotsTxt.parse(body, properties.robotsAgent());
                robots.crawlDelay().ifPresent(robotsDelay -> {
                    Duration capped = robotsDelay.compareTo(MAX_CRAWL_DELAY) > 0 ? MAX_CRAWL_DELAY : robotsDelay;
                    if (capped.compareTo(delay) > 0) {
                        delay = capped;
                    }
                });
            });
        }

        private boolean robotsAllowed(String url) {
            return !properties.respectRobotsTxt() || robots.isAllowed(UrlUtils.pathAndQuery(url));
        }

        private void loadSitemap(String homeUrl) {
            List<String> sitemapUrls = new ArrayList<>(robots.sitemaps());
            if (sitemapUrls.isEmpty()) {
                sitemapUrls.add(UrlUtils.origin(homeUrl) + "/sitemap.xml");
            }
            int added = 0;
            int nestedLoaded = 0;
            for (int i = 0; i < sitemapUrls.size() && added < properties.maxSitemapUrls(); i++) {
                Optional<String> xml = fetchText(sitemapUrls.get(i));
                if (xml.isEmpty()) {
                    continue;
                }
                SitemapParser.Sitemap sitemap;
                try {
                    sitemap = SitemapParser.parse(xml.get());
                } catch (RuntimeException e) {
                    log.info("Не удалось разобрать {}: {}", sitemapUrls.get(i), e.getMessage());
                    continue;
                }
                for (String nested : sitemap.sitemapUrls()) {
                    if (nestedLoaded++ < MAX_NESTED_SITEMAPS) {
                        sitemapUrls.add(nested);
                    }
                }
                for (String pageUrl : sitemap.pageUrls()) {
                    if (added >= properties.maxSitemapUrls()) {
                        break;
                    }
                    Optional<String> normalized = UrlUtils.normalize(pageUrl);
                    if (normalized.isPresent()) {
                        enqueue(normalized.get(), "");
                        added++;
                    }
                }
            }
            log.info("Из sitemap получено адресов: {}", added);
        }

        /** Загружает текстовый ресурс через HTTP-клиент контекста (тот же User-Agent и прокси). */
        private Optional<String> fetchText(String url) {
            try {
                APIResponse response = context.request().get(url, RequestOptions.create()
                        .setTimeout(properties.navigationTimeout().toMillis())
                        .setMaxRedirects(5));
                try {
                    return response.ok() ? Optional.of(response.text()) : Optional.empty();
                } finally {
                    response.dispose();
                }
            } catch (PlaywrightException e) {
                log.info("Не удалось загрузить {}: {}", url, firstLine(e.getMessage()));
                return Optional.empty();
            }
        }

        private void pause() {
            if (delay.isZero() || delay.isNegative()) {
                return;
            }
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Обход прерван", e);
            }
        }
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "неизвестная ошибка";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
