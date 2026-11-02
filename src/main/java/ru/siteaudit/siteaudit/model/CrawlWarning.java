package ru.siteaudit.siteaudit.model;

/**
 * Проблема при обходе, не связанная с нарушениями: страница не открылась, запрещена robots.txt и т.п.
 *
 * @param url     адрес, с которым возникла проблема
 * @param message понятное описание
 */
public record CrawlWarning(String url, String message) {
}
