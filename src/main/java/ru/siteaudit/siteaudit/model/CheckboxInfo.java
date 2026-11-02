package ru.siteaudit.siteaudit.model;

import java.util.List;

/**
 * Чекбокс формы.
 *
 * @param name      атрибут name (или id)
 * @param checked   отмечен ли заранее (атрибут checked в отрендеренном HTML)
 * @param labelText текст подписи
 * @param links     абсолютные URL ссылок внутри подписи
 */
public record CheckboxInfo(String name, boolean checked, String labelText, List<String> links) {
    public CheckboxInfo {
        labelText = labelText == null ? "" : labelText;
        links = List.copyOf(links);
    }
}
