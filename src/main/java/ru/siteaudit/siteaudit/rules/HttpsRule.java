package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.FormInfo;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Сайт открывается по HTTP или форма отправляет данные по HTTP. */
@Component
public class HttpsRule implements Rule {

    public static final String ID = "https";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        List<Finding> findings = new ArrayList<>();
        List<String> httpPages = pages.stream().map(PageSnapshot::url).filter(HttpsRule::isHttp).toList();
        if (!httpPages.isEmpty()) {
            findings.add(new Finding(
                    ID,
                    Severity.CRITICAL,
                    "Сайт работает без шифрования (HTTP)",
                    "Страницы сайта открываются по незащищённому протоколу HTTP. Всё, что посетитель вводит "
                            + "в формы (имя, телефон, email), передаётся в открытом виде и может быть перехвачено.",
                    httpPages.getFirst(),
                    "Страницы по HTTP: " + Evidence.list(httpPages),
                    "pd-security",
                    "Рекомендуем подключить SSL-сертификат и настроить автоматическое перенаправление "
                            + "со всех HTTP-адресов на HTTPS."));
        }
        // Формы на HTTPS-страницах, которые отправляют данные по HTTP
        Map<String, List<String>> insecureActions = new LinkedHashMap<>();
        Map<String, FormInfo> forms = new LinkedHashMap<>();
        for (PageSnapshot page : pages) {
            if (isHttp(page.url())) {
                continue; // уже учтено выше
            }
            for (FormInfo form : page.forms()) {
                if (isHttp(form.action())) {
                    insecureActions.computeIfAbsent(form.action(), k -> new ArrayList<>()).add(page.url());
                    forms.putIfAbsent(form.action(), form);
                }
            }
        }
        insecureActions.forEach((action, pageUrls) -> findings.add(new Finding(
                ID,
                Severity.CRITICAL,
                "Форма отправляет данные без шифрования",
                "Страница открыта по HTTPS, но форма на ней отправляет данные на адрес с HTTP. "
                        + "Введённые посетителем данные передаются в открытом виде.",
                pageUrls.getFirst(),
                Evidence.describeForm(forms.get(action)) + ". Страницы: " + Evidence.list(pageUrls),
                "pd-security",
                "Рекомендуем изменить адрес отправки формы на HTTPS.")));
        return findings;
    }

    private static boolean isHttp(String url) {
        return url != null && url.toLowerCase(Locale.ROOT).startsWith("http://");
    }
}
