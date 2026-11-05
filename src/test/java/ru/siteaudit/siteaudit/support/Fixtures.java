package ru.siteaudit.siteaudit.support;

import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.RawPage;
import ru.siteaudit.siteaudit.parser.PageParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Загрузка HTML-фикстур из src/test/resources/fixtures. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String html(String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("Нет фикстуры " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static RawPage raw(String name, String url, List<String> requests) {
        return new RawPage(url, html(name), requests);
    }

    public static PageSnapshot snapshot(String name, String url, String... requests) {
        return new PageParser().parse(raw(name, url, List.of(requests)));
    }
}
