package ru.siteaudit.siteaudit.model;

import java.util.List;

/**
 * Разобранная страница — всё, что нужно правилам. Строится из {@link RawPage} парсером,
 * поэтому правила тестируются без браузера.
 *
 * @param url                       URL страницы
 * @param requestsBeforeInteraction сетевые запросы до любого взаимодействия
 * @param forms                     формы, включая «формы без тега form»
 * @param legalLinks                ссылки на юридические документы: политика, согласие, cookie, оферта
 * @param externalLinks             ссылки на другие домены (для проверки рекламной маркировки)
 * @param hasCookieBanner           найден ли блок с уведомлением о cookie
 * @param visibleText               видимый текст страницы (для анализа текста политики)
 */
public record PageSnapshot(
        String url,
        List<String> requestsBeforeInteraction,
        List<FormInfo> forms,
        List<LinkInfo> legalLinks,
        List<LinkInfo> externalLinks,
        boolean hasCookieBanner,
        String visibleText
) {
    public PageSnapshot {
        requestsBeforeInteraction = List.copyOf(requestsBeforeInteraction);
        forms = List.copyOf(forms);
        legalLinks = List.copyOf(legalLinks);
        externalLinks = List.copyOf(externalLinks);
        visibleText = visibleText == null ? "" : visibleText;
    }
}
