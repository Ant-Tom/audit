package ru.siteaudit.siteaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import ru.siteaudit.siteaudit.cli.AuditCommandLineRunner;

import java.util.Arrays;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SiteAuditApplication {

    public static void main(String[] args) {
        boolean web = isWebMode(args);
        SpringApplication app = new SpringApplication(SiteAuditApplication.class);
        app.setWebApplicationType(web ? WebApplicationType.SERVLET : WebApplicationType.NONE);
        ConfigurableApplicationContext context = app.run(args);
        // В консольном режиме завершаемся сразу после аудита (закрывая браузер) с кодом из CLI-раннера.
        // В веб-режиме приложение работает, пока его не остановят (Ctrl-C).
        if (!web) {
            System.exit(SpringApplication.exit(context));
        }
    }

    static boolean isWebMode(String[] args) {
        String flag = "--" + AuditCommandLineRunner.WEB_OPTION;
        return Arrays.stream(args).anyMatch(a -> a.equals(flag) || a.equals(flag + "=true"));
    }
}
