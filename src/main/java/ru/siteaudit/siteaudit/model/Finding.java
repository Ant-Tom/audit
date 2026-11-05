package ru.siteaudit.siteaudit.model;

/**
 * Обнаруженный признак возможного нарушения.
 *
 * @param ruleId         идентификатор правила
 * @param severity       уровень важности
 * @param title          короткий заголовок для владельца сайта
 * @param description    что обнаружено и почему это важно
 * @param pageUrl        страница, на которой обнаружен признак
 * @param evidence       что конкретно найдено: селектор, URL запроса, фрагмент
 * @param legalRef       ключ нормы в legal.yml
 * @param recommendation что рекомендуется проверить или сделать
 */
public record Finding(
        String ruleId,
        Severity severity,
        String title,
        String description,
        String pageUrl,
        String evidence,
        String legalRef,
        String recommendation
) {
}
