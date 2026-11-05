package ru.siteaudit.siteaudit.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.RawPage;
import ru.siteaudit.siteaudit.parser.PageParser;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Краулер на локальном статическом сайте. Нужен установленный Chromium для Playwright.
 * Запуск: {@code mvn test -Pintegration}.
 */
@Tag("integration")
class SiteCrawlerIntegrationTest {

    private static HttpServer server;
    private static String base;
    private static Playwright playwright;
    private static Browser browser;

    private static final String HOME = """
            <!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Тестовый сайт</title>
            <style>.cc { position: fixed; bottom: 0; left: 0; right: 0; }</style>
            <script>
              // «Счётчик» грузится с задержкой — краулер должен дождаться networkidle
              setTimeout(function () {
                var s = document.createElement('script'); s.src = '/fake-tracker.js?id=42'; document.head.appendChild(s);
              }, 300);
              // Чекбокс отмечается скриптом: атрибута checked в разметке нет
              document.addEventListener('DOMContentLoaded', function () {
                document.getElementById('agree').checked = true;
              });
            </script></head>
            <body>
              <a href="/contacts">Контакты</a>
              <a href="/privacy">Политика конфиденциальности</a>
              <a href="/secret/">Закрыто robots.txt</a>
              <a href="/blog">Блог</a>
              <a href="/missing">Битая ссылка</a>
              <a href="https://external.example/">Внешняя</a>
              <form action="/send" method="post">
                <input name="phone" type="tel">
                <input type="checkbox" id="agree"><label for="agree">Согласен на обработку персональных данных</label>
                <button>Отправить</button>
              </form>
              <div class="cc"><span>Мы используем cookie</span></div>
            </body></html>""";

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        Map<String, String> pages = Map.of(
                "/", HOME,
                "/contacts", "<html><body><h1>Контакты</h1><form><input name='email' type='email'></form></body></html>",
                "/privacy", "<html><body><h1>Политика</h1><p>Оператор ООО «Тест»</p></body></html>",
                "/blog", "<html><body><h1>Блог</h1></body></html>",
                "/from-sitemap", "<html><body><h1>Страница из sitemap</h1></body></html>",
                "/secret/", "<html><body>секрет</body></html>");
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body;
            String type = "text/html; charset=utf-8";
            int status = 200;
            if (path.equals("/robots.txt")) {
                body = "User-agent: *\nDisallow: /secret/\n";
                type = "text/plain";
            } else if (path.equals("/sitemap.xml")) {
                body = "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\"><url><loc>"
                        + base + "/from-sitemap</loc></url></urlset>";
                type = "application/xml";
            } else if (path.equals("/fake-tracker.js")) {
                body = "/* tracker */";
                type = "application/javascript";
            } else if (pages.containsKey(path)) {
                body = pages.get(path);
            } else {
                body = "not found";
                status = 404;
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        playwright = Playwright.create();
        browser = BrowserConfig.launch(playwright, new CrawlerProperties.Browser(true, "", "", ""));
    }

    @AfterAll
    static void stop() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    private static SiteCrawler crawler() {
        CrawlerProperties properties = new CrawlerProperties(15, 5, 100,
                Duration.ofSeconds(15), Duration.ofSeconds(5), Duration.ZERO, 1,
                "SiteAuditBot-Test", "SiteAuditBot", true, true,
                new CrawlerProperties.Browser(true, "", "", ""));
        StaticListableBeanFactory beans = new StaticListableBeanFactory(Map.of("browser", browser));
        return new SiteCrawler(beans.getBeanProvider(Browser.class), properties);
    }

    @Test
    void crawlsSiteRespectingRobotsAndSitemap() {
        CrawlResult result = crawler().crawl(base + "/");

        List<String> urls = result.pages().stream().map(RawPage::url).toList();
        assertThat(urls.getFirst()).isEqualTo(base + "/");
        // Юридические страницы и «контакты» идут раньше блога
        assertThat(urls).containsSubsequence(base + "/privacy", base + "/contacts", base + "/blog");
        assertThat(urls).contains(base + "/from-sitemap");
        assertThat(urls).doesNotContain(base + "/secret/");
        assertThat(result.warnings()).anySatisfy(w -> assertThat(w.message()).contains("robots.txt"));
        assertThat(result.warnings()).anySatisfy(w -> {
            assertThat(w.url()).isEqualTo(base + "/missing");
            assertThat(w.message()).contains("404");
        });
    }

    @Test
    void capturesDelayedRequestsAndRenderedState() {
        CrawlResult result = crawler().crawl(base + "/");
        RawPage home = result.pages().getFirst();

        assertThat(home.requestsBeforeInteraction()).anyMatch(u -> u.contains("/fake-tracker.js?id=42"));

        PageSnapshot snapshot = new PageParser().parse(home);
        assertThat(snapshot.hasCookieBanner()).as("fixed-баннер из CSS-класса").isTrue();
        assertThat(snapshot.forms().getFirst().checkboxes()).extracting(CheckboxInfo::checked).containsExactly(true);
        assertThat(snapshot.externalLinks()).extracting(l -> l.href()).containsExactly("https://external.example/");
    }
}
