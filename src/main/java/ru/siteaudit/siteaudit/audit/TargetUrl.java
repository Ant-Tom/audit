package ru.siteaudit.siteaudit.audit;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/** Приведение адреса сайта, введённого человеком, к виду, пригодному для проверки. Общее для CLI и веб-интерфейса. */
public final class TargetUrl {

    /** Ограничение длины адреса из формы — защита от мусорного ввода. */
    public static final int MAX_LENGTH = 2000;

    private TargetUrl() {
    }

    /**
     * @param raw адрес как его ввели: {@code example.ru}, {@code https://example.ru/page}
     * @return адрес со схемой http/https или пусто, если это не адрес сайта
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String candidate = raw.trim();
        if (candidate.isEmpty() || candidate.length() > MAX_LENGTH) {
            return Optional.empty();
        }
        if (!candidate.toLowerCase(Locale.ROOT).matches("^https?://.*")) {
            if (candidate.contains("://")) {
                return Optional.empty(); // ftp:// и прочее
            }
            candidate = "https://" + candidate;
        }
        try {
            URI uri = URI.create(candidate);
            String host = uri.getHost();
            return host == null || !host.contains(".") && !host.equals("localhost")
                    ? Optional.empty() : Optional.of(candidate);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
