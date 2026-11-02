package ru.siteaudit.siteaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SiteAuditApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(SiteAuditApplication.class, args);
        // В консольном режиме завершаемся сразу после аудита (закрывая браузер) с кодом из CLI-раннера.
        // Когда появится веб-режим, приложение продолжит работать.
        if (!(context instanceof WebServerApplicationContext)) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
