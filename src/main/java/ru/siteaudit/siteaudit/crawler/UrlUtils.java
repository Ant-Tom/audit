package ru.siteaudit.siteaudit.crawler;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Нормализация URL и проверка «тот же сайт». */
public final class UrlUtils {

    private static final Pattern NON_HTML_EXTENSION = Pattern.compile(
            "(?i).*\\.(pdf|docx?|xlsx?|pptx?|rtf|odt|zip|rar|7z|gz|tar|jpe?g|png|gif|webp|svg|ico|bmp|tiff?"
                    + "|mp3|mp4|avi|mov|webm|ogg|wav|css|js|json|xml|txt|apk|exe|dmg|woff2?|ttf|eot)$");

    private static final Set<String> HTTP_SCHEMES = Set.of("http", "https");

    private UrlUtils() {
    }

    /**
     * Приводит URL к каноническому виду для дедупликации: без фрагмента, хост в нижнем регистре,
     * без порта по умолчанию, пустой путь превращается в «/».
     */
    public static Optional<String> normalize(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = new URI(url.trim()).normalize();
            String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
            if (scheme == null || !HTTP_SCHEMES.contains(scheme) || uri.getHost() == null) {
                return Optional.empty();
            }
            int port = uri.getPort();
            if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
                port = -1;
            }
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            StringBuilder sb = new StringBuilder()
                    .append(scheme).append("://").append(uri.getHost().toLowerCase(Locale.ROOT));
            if (port != -1) {
                sb.append(':').append(port);
            }
            sb.append(path);
            if (uri.getRawQuery() != null) {
                sb.append('?').append(uri.getRawQuery());
            }
            return Optional.of(sb.toString());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Хост без «www.» в нижнем регистре; пустая строка, если URL некорректен. */
    public static String siteHost(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean sameSite(String url, String siteHost) {
        return !siteHost.isEmpty() && siteHost.equals(siteHost(url));
    }

    /** Ссылка, скорее всего, ведёт на файл, а не на HTML-страницу. */
    public static boolean looksLikeFile(String url) {
        try {
            String path = URI.create(url).getPath();
            return path != null && NON_HTML_EXTENSION.matcher(path).matches();
        } catch (Exception e) {
            return false;
        }
    }

    public static String origin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }

    /** Путь с query-строкой — то, с чем сравниваются правила robots.txt. */
    public static String pathAndQuery(String url) {
        URI uri = URI.create(url);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }
}
