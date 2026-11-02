package ru.siteaudit.siteaudit.crawler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Proxy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.nio.file.Path;

/**
 * Один Playwright и один Browser на всё приложение. Оба бина ленивые: в режиме «usage» и в будущем
 * веб-режиме без проверок браузер не запускается. Spring закрывает их при остановке контекста.
 *
 * <p>Объекты Playwright не потокобезопасны. В MVP аудит выполняется в одном потоке
 * ({@link SiteCrawler#crawl} синхронизирован). Для очереди проверок понадобится пул «Playwright + Browser
 * на рабочий поток» — это изменение затронет только этот пакет.
 */
@Configuration
@EnableConfigurationProperties(CrawlerProperties.class)
public class BrowserConfig {

    @Bean(destroyMethod = "close")
    @Lazy
    public Playwright playwright() {
        return Playwright.create();
    }

    @Bean(destroyMethod = "close")
    @Lazy
    public Browser browser(Playwright playwright, CrawlerProperties properties) {
        return launch(playwright, properties.browser());
    }

    /** Вынесено для повторного использования в тестах без Spring-контекста. */
    public static Browser launch(Playwright playwright, CrawlerProperties.Browser settings) {
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions().setHeadless(settings.headless());
        if (!settings.executablePath().isBlank()) {
            options.setExecutablePath(Path.of(settings.executablePath()));
        }
        if (!settings.proxyServer().isBlank()) {
            options.setProxy(new Proxy(settings.proxyServer()).setBypass(settings.proxyBypass()));
        }
        return playwright.chromium().launch(options);
    }
}
