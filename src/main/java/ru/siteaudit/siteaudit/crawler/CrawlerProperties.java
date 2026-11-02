package ru.siteaudit.siteaudit.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки обхода сайта (секция {@code crawler} в application.yml).
 *
 * @param maxPages             сколько внутренних страниц обходить помимо главной
 * @param maxLegalPages        сколько страниц юридических документов (политика, согласие, cookie, оферта)
 *                             обходить сверх {@code maxPages}
 * @param maxSitemapUrls       сколько URL максимум брать из sitemap.xml
 * @param navigationTimeout    таймаут загрузки страницы
 * @param networkIdleTimeout   сколько ждать «тишины в сети» после загрузки (счётчики грузятся с задержкой)
 * @param delayBetweenRequests пауза между страницами
 * @param retries              сколько повторов при ошибке загрузки страницы
 * @param userAgent            User-Agent краулера
 * @param robotsAgent          токен для поиска своей секции в robots.txt
 * @param respectRobotsTxt     соблюдать robots.txt
 * @param readSitemap          читать sitemap.xml
 * @param browser              настройки браузера
 */
@ConfigurationProperties("crawler")
public record CrawlerProperties(
        @DefaultValue("15") int maxPages,
        @DefaultValue("5") int maxLegalPages,
        @DefaultValue("500") int maxSitemapUrls,
        @DefaultValue("30s") Duration navigationTimeout,
        @DefaultValue("10s") Duration networkIdleTimeout,
        @DefaultValue("1s") Duration delayBetweenRequests,
        @DefaultValue("1") int retries,
        @DefaultValue("Mozilla/5.0 (compatible; SiteAuditBot/0.1)") String userAgent,
        @DefaultValue("SiteAuditBot") String robotsAgent,
        @DefaultValue("true") boolean respectRobotsTxt,
        @DefaultValue("true") boolean readSitemap,
        @DefaultValue Browser browser
) {

    /**
     * @param headless       запускать без окна
     * @param executablePath путь к Chromium; пусто — браузер, установленный Playwright
     * @param proxyServer    прокси для браузера (например {@code http://127.0.0.1:3128}); пусто — без прокси
     * @param proxyBypass    список хостов в обход прокси
     */
    public record Browser(
            @DefaultValue("true") boolean headless,
            @DefaultValue("") String executablePath,
            @DefaultValue("") String proxyServer,
            @DefaultValue("localhost,127.0.0.1") String proxyBypass
    ) {
    }
}
