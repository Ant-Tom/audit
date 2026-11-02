package ru.siteaudit.siteaudit.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Справочник трекеров и иностранных сервисов из trackers.yml. */
@ConfigurationProperties("trackers")
public record TrackerProperties(List<Service> services) {

    public TrackerProperties {
        services = services == null ? List.of() : List.copyOf(services);
    }

    /** Первый сервис из справочника, которому принадлежит URL запроса. */
    public Optional<Service> match(String requestUrl) {
        return services.stream().filter(s -> s.matches(requestUrl)).findFirst();
    }

    /**
     * @param name    название для отчёта
     * @param domains домены; запись может содержать префикс пути: {@code vk.com/rtrg}
     * @param tracker счётчик / пиксель / аналитика
     * @param foreign иностранный сервис
     */
    public record Service(String name, List<String> domains, boolean tracker, boolean foreign) {

        public Service {
            domains = domains == null ? List.of() : List.copyOf(domains);
        }

        public boolean matches(String requestUrl) {
            URI uri;
            try {
                uri = URI.create(requestUrl);
            } catch (IllegalArgumentException e) {
                return false;
            }
            if (uri.getHost() == null) {
                return false;
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            for (String entry : domains) {
                String e = entry.toLowerCase(Locale.ROOT);
                int slash = e.indexOf('/');
                String domain = slash < 0 ? e : e.substring(0, slash);
                String pathPrefix = slash < 0 ? "" : e.substring(slash);
                boolean hostMatches = host.equals(domain) || host.endsWith("." + domain);
                if (hostMatches && path.startsWith(pathPrefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
