package ru.siteaudit.siteaudit.parser;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Эвристика поиска cookie-баннера: блок с текстом про cookie, который либо плавающий
 * (fixed/sticky — по атрибуту {@code data-sa-position}, который проставляет краулер, или по inline-стилю),
 * либо по классу/id похож на баннер или окно согласия.
 *
 * <p>Ограничения: баннеры внутри iframe и closed shadow DOM не видны; баннер, который
 * показывается только после прокрутки или с задержкой больше networkIdle-таймаута, может быть пропущен.
 */
public final class CookieBannerDetector {

    static final Pattern COOKIE_TEXT = Pattern.compile("cookie|куки|кукис");
    private static final Pattern FLOATING_STYLE = Pattern.compile("position\\s*:\\s*(fixed|sticky)");
    private static final Pattern BANNER_HINT = Pattern.compile(
            "cookie|consent|gdpr|banner|popup|pop-up|notice|notification|alert|modal|toast|bar|warning|agree|cmp");
    /** Текст баннера короткий; длинный текст с «cookie» — скорее страница политики. */
    private static final int MAX_BANNER_TEXT = 1500;
    private static final int MAX_ANCESTOR_DEPTH = 6;

    private CookieBannerDetector() {
    }

    public static boolean hasCookieBanner(Document doc) {
        for (Element el : doc.body() == null ? new org.jsoup.select.Elements() : doc.body().getAllElements()) {
            String ownText = el.ownText().toLowerCase(Locale.ROOT);
            if (!COOKIE_TEXT.matcher(ownText).find()) {
                continue;
            }
            Element current = el;
            for (int depth = 0; current != null && depth <= MAX_ANCESTOR_DEPTH; depth++) {
                if (current.normalName().equals("body")) {
                    break;
                }
                if (current.text().length() > MAX_BANNER_TEXT) {
                    break;
                }
                if (looksLikeBanner(current)) {
                    return true;
                }
                current = current.parent();
            }
        }
        return false;
    }

    private static boolean looksLikeBanner(Element el) {
        if (el.hasAttr("data-sa-position")) {
            return true;
        }
        if (FLOATING_STYLE.matcher(el.attr("style").toLowerCase(Locale.ROOT)).find()) {
            return true;
        }
        if (el.normalName().equals("dialog") || "dialog".equals(el.attr("role")) || "alertdialog".equals(el.attr("role"))) {
            return true;
        }
        String classAndId = (el.className() + " " + el.id()).toLowerCase(Locale.ROOT);
        return !el.normalName().equals("footer") && !classAndId.contains("footer")
                && BANNER_HINT.matcher(classAndId).find();
    }
}
