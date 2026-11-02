package ru.siteaudit.siteaudit.model;

import java.util.List;

/**
 * Результат рендеринга одной страницы в браузере.
 *
 * @param url                       финальный URL страницы (после редиректов)
 * @param renderedHtml              HTML после выполнения JavaScript
 * @param requestsBeforeInteraction URL всех сетевых запросов, сделанных страницей
 *                                  до любого взаимодействия пользователя (клики не выполняются)
 */
public record RawPage(String url, String renderedHtml, List<String> requestsBeforeInteraction) {
    public RawPage {
        requestsBeforeInteraction = List.copyOf(requestsBeforeInteraction);
    }
}
