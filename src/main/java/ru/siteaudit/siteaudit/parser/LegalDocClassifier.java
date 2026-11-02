package ru.siteaudit.siteaudit.parser;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Определяет по тексту ссылки и её адресу, на какой юридический документ она ведёт.
 * Единое место для ключевых слов — их используют и парсер, и краулер, и правила.
 */
public final class LegalDocClassifier {

    public enum DocKind {
        /** Политика обработки персональных данных / политика конфиденциальности. */
        POLICY,
        /** Согласие на обработку персональных данных. */
        CONSENT,
        /** Политика / уведомление об использовании cookie. */
        COOKIE,
        /** Оферта, пользовательское соглашение, условия использования. */
        OFFER
    }

    private static final Pattern COOKIE = Pattern.compile("cookie|куки|кукис");
    private static final Pattern CONSENT_TEXT = Pattern.compile("согласи[еяю]|consent");
    private static final Pattern CONSENT_HREF = Pattern.compile("soglasi|consent|agree(?!ment)|согласи");
    private static final Pattern POLICY_TEXT = Pattern.compile("политик|конфиденц|privacy|personal data|персональн");
    private static final Pattern POLICY_HREF = Pattern.compile(
            "politi|polic|privacy|konfiden|confiden|personal|pers[-_]?dan|политик|конфиденц|персональн");
    private static final Pattern OFFER = Pattern.compile(
            "оферт|oferta|public[-_]?offer|пользовательск\\S* соглашени|user[-_ ]?agreement|terms|условия использ");

    private LegalDocClassifier() {
    }

    public static Set<DocKind> classify(String text, String href) {
        String t = lower(text);
        String h = lower(decode(href));
        Set<DocKind> kinds = EnumSet.noneOf(DocKind.class);

        boolean cookie = COOKIE.matcher(t).find() || COOKIE.matcher(h).find();
        if (cookie) {
            kinds.add(DocKind.COOKIE);
        }
        boolean consent = CONSENT_TEXT.matcher(t).find() || CONSENT_HREF.matcher(h).find();
        if (consent && !cookie) {
            kinds.add(DocKind.CONSENT);
        }
        // «Согласие на обработку персональных данных» — это согласие, а не политика,
        // поэтому слово «персональн» само по себе делает ссылку политикой только без признаков согласия.
        boolean policyByText = POLICY_TEXT.matcher(t).find()
                && !(consent && !t.contains("политик") && !t.contains("конфиденц"));
        boolean policyByHref = !consent && POLICY_HREF.matcher(h).find();
        if ((policyByText || policyByHref) && !cookie) {
            kinds.add(DocKind.POLICY);
        }
        if (OFFER.matcher(t).find() || OFFER.matcher(h).find()) {
            kinds.add(DocKind.OFFER);
        }
        return kinds;
    }

    public static boolean isLegal(String text, String href) {
        return !classify(text, href).isEmpty();
    }

    public static boolean is(DocKind kind, String text, String href) {
        return classify(text, href).contains(kind);
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    private static String decode(String href) {
        if (href == null) {
            return "";
        }
        try {
            return URLDecoder.decode(href, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return href;
        }
    }
}
