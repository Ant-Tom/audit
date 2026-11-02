package ru.siteaudit.siteaudit.crawler;

import ru.siteaudit.siteaudit.parser.LegalDocClassifier;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Оценивает, насколько ссылку важно посетить. Формы обычно находятся на страницах контактов, заказа,
 * корзины, обратной связи, записи — о наличии формы мы узнаём только после рендеринга, поэтому
 * приоритет определяется по тексту и адресу ссылки.
 */
public final class LinkPrioritizer {

    /** Юридические документы обходятся всегда, отдельным лимитом. */
    public static final int LEGAL = Integer.MAX_VALUE;

    private static final Pattern HIGH = Pattern.compile(
            "контакт|заказ|корзин|обратн|заявк|запис|оформ|регистрац|подписк|консультац|связ"
                    + "|contact|order|cart|basket|checkout|feedback|callback|signup|register|subscribe|booking");
    private static final Pattern MEDIUM = Pattern.compile(
            "услуг|каталог|товар|доставк|оплат|о компании|о нас|цены|прайс"
                    + "|service|catalog|product|delivery|payment|about|price");
    private static final Pattern LOW = Pattern.compile(
            "блог|новост|статьи|tag|page=|/page/|blog|news|article|\\?sort|\\?filter");

    private LinkPrioritizer() {
    }

    public static int score(String text, String href) {
        if (LegalDocClassifier.isLegal(text, href)) {
            return LEGAL;
        }
        String haystack = (text == null ? "" : text) + " " + decode(href);
        haystack = haystack.toLowerCase(Locale.ROOT);
        int score = 10;
        if (HIGH.matcher(haystack).find()) {
            score += 100;
        }
        if (MEDIUM.matcher(haystack).find()) {
            score += 30;
        }
        if (LOW.matcher(haystack).find()) {
            score -= 20;
        }
        // Чем короче путь, тем ближе к корню и тем вероятнее это «основная» страница
        long depth = decode(href).chars().filter(c -> c == '/').count();
        return score - (int) Math.min(depth, 9);
    }

    private static String decode(String href) {
        try {
            return URLDecoder.decode(href == null ? "" : href, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return href;
        }
    }
}
