package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.legal.TrackerProperties;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Запросы к иностранным сервисам — признак возможной трансграничной передачи персональных данных. */
@Component
public class ForeignServicesRule implements Rule {

    public static final String ID = "foreign-services";

    private final TrackerProperties trackers;

    public ForeignServicesRule(TrackerProperties trackers) {
        this.trackers = trackers;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        Map<String, Set<String>> requests = new LinkedHashMap<>();
        Map<String, Set<String>> pageUrls = new LinkedHashMap<>();
        for (PageSnapshot page : pages) {
            for (String request : page.requestsBeforeInteraction()) {
                trackers.match(request).filter(TrackerProperties.Service::foreign).ifPresent(service -> {
                    requests.computeIfAbsent(service.name(), k -> new LinkedHashSet<>()).add(request);
                    pageUrls.computeIfAbsent(service.name(), k -> new LinkedHashSet<>()).add(page.url());
                });
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (var entry : requests.entrySet()) {
            String service = entry.getKey();
            List<String> urls = List.copyOf(pageUrls.get(service));
            findings.add(new Finding(
                    ID,
                    Severity.WARNING,
                    "Используется иностранный сервис «" + service + "»",
                    "Страницы сайта обращаются к иностранному сервису «" + service + "». Браузер посетителя "
                            + "передаёт ему как минимум IP-адрес, а иногда cookie и другие данные. Это может считаться "
                            + "трансграничной передачей персональных данных, о которой нужно заранее уведомить "
                            + "Роскомнадзор.",
                    urls.getFirst(),
                    "Запросы: " + Evidence.list(entry.getValue()) + ". Страницы: " + Evidence.list(urls),
                    "pd-cross-border",
                    "Рекомендуем проверить, нужен ли этот сервис. Если нужен — оцените, передаются ли через него "
                            + "персональные данные, и при необходимости подайте уведомление о трансграничной передаче. "
                            + "Многие сервисы можно заменить российскими аналогами или подключать локально "
                            + "(например, шрифты — с собственного сервера)."));
        }
        return findings;
    }
}
