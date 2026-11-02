package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.legal.TrackerProperties;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Внешние ссылки, похожие на рекламные или партнёрские, без токена erid. Только INFO: по ссылке нельзя
 * понять, является ли она рекламой, — это решает владелец сайта.
 */
@Component
public class AdEridRule implements Rule {

    public static final String ID = "ad-erid";

    private static final Pattern ERID = Pattern.compile("[?&#]erid=", Pattern.CASE_INSENSITIVE);

    private final TrackerProperties trackers;

    public AdEridRule(TrackerProperties trackers) {
        this.trackers = trackers;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        Map<String, Set<String>> linksToPages = new LinkedHashMap<>();
        for (PageSnapshot page : pages) {
            for (LinkInfo link : page.externalLinks()) {
                String href = link.href();
                if (trackers.adLinks().looksLikeAd(href) && !ERID.matcher(href.toLowerCase(Locale.ROOT)).find()) {
                    linksToPages.computeIfAbsent(href, k -> new LinkedHashSet<>()).add(page.url());
                }
            }
        }
        if (linksToPages.isEmpty()) {
            return List.of();
        }
        String firstPage = linksToPages.values().iterator().next().iterator().next();
        Set<String> allPages = new LinkedHashSet<>();
        linksToPages.values().forEach(allPages::addAll);
        return List.of(new Finding(
                ID,
                Severity.INFO,
                "Ссылки, похожие на рекламные, без маркировки erid",
                "На сайте есть ссылки на рекламные или партнёрские сети без идентификатора рекламы (erid). "
                        + "Мы не можем определить, является ли каждая из них рекламой. Если это реклама — "
                        + "она должна быть промаркирована; если нет — эту находку можно не учитывать.",
                firstPage,
                "Ссылки: " + Evidence.list(linksToPages.keySet()) + ". Страницы: " + Evidence.list(allPages),
                "ad-marking",
                "Рекомендуем проверить, есть ли среди этих ссылок реклама. Для рекламных ссылок получите токен "
                        + "erid через оператора рекламных данных и добавьте пометку «Реклама» и сведения о рекламодателе."));
    }
}
