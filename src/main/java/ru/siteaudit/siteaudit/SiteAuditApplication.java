package ru.siteaudit.siteaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SiteAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(SiteAuditApplication.class, args);
    }
}
