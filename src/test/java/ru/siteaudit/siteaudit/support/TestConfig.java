package ru.siteaudit.siteaudit.support;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.legal.TrackerProperties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/** Загружает настоящие legal.yml / trackers.yml без Spring-контекста. */
public final class TestConfig {

    private TestConfig() {
    }

    public static TrackerProperties trackers() {
        return bind("trackers.yml", "trackers", TrackerProperties.class);
    }

    public static LegalProperties legal() {
        return bind("legal.yml", "legal", LegalProperties.class);
    }

    private static <T> T bind(String file, String prefix, Class<T> type) {
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
            return new Binder(ConfigurationPropertySources.from(sources)).bind(prefix, type).get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
