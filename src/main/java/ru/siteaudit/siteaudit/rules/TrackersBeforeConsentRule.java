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

/**
 * Счётчики и пиксели загружаются до согласия на cookie. Краулер ни на что не нажимает, поэтому любой
 * запрос к трекеру в собранном трафике сделан до согласия.
 */
@Component
public class TrackersBeforeConsentRule implements Rule {

    public static final String ID = "trackers-before-consent";

    private final TrackerProperties trackers;

    public TrackersBeforeConsentRule(TrackerProperties trackers) {
        this.trackers = trackers;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        Map<String, Set<String>> requestsByTracker = new LinkedHashMap<>();
        Map<String, Set<String>> pagesByTracker = new LinkedHashMap<>();
        for (PageSnapshot page : pages) {
            for (String request : page.requestsBeforeInteraction()) {
                trackers.match(request).filter(TrackerProperties.Service::tracker).ifPresent(service -> {
                    requestsByTracker.computeIfAbsent(service.name(), k -> new LinkedHashSet<>()).add(request);
                    pagesByTracker.computeIfAbsent(service.name(), k -> new LinkedHashSet<>()).add(page.url());
                });
            }
        }
        List<Finding> findings = new ArrayList<>();
        for (var entry : requestsByTracker.entrySet()) {
            String tracker = entry.getKey();
            List<String> pageUrls = List.copyOf(pagesByTracker.get(tracker));
            findings.add(new Finding(
                    ID,
                    Severity.CRITICAL,
                    "«" + tracker + "» загружается до согласия посетителя",
                    "Сервис «" + tracker + "» начинает работать сразу при открытии страницы — до того, как посетитель "
                            + "согласился на cookie и обработку данных. Мы не нажимали никаких кнопок на сайте, поэтому "
                            + "все эти запросы сделаны без согласия. Такие сервисы обычно получают cookie, идентификаторы "
                            + "и IP-адрес посетителя.",
                    pageUrls.getFirst(),
                    "Запросы: " + Evidence.list(entry.getValue()) + ". Страницы: " + Evidence.list(pageUrls),
                    "pd-trackers-consent",
                    "Рекомендуем проверить, на каком основании работает сервис. Если основание — согласие, "
                            + "загружайте счётчик только после того, как посетитель нажал «Принять» в cookie-баннере."));
        }
        return findings;
    }
}
