package ru.siteaudit.siteaudit.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/** Печатает адрес веб-интерфейса после старта: логи Spring и Tomcat в этом приложении приглушены до WARN. */
@Component
public class WebStartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(WebStartupLogger.class);

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        String address = event.getApplicationContext().getEnvironment().getProperty("server.address", "localhost");
        String host = address.isBlank() || address.equals("0.0.0.0") || address.equals("127.0.0.1") ? "localhost" : address;
        log.info("Веб-интерфейс запущен: http://{}:{}/  (остановить — Ctrl-C)", host, event.getWebServer().getPort());
    }
}
