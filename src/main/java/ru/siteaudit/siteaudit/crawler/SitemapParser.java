package ru.siteaudit.siteaudit.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

import java.util.ArrayList;
import java.util.List;

/** Разбор sitemap.xml: обычный urlset и sitemapindex. */
public final class SitemapParser {

    /**
     * @param pageUrls    адреса страниц из {@code <url><loc>}
     * @param sitemapUrls адреса вложенных карт из {@code <sitemap><loc>}
     */
    public record Sitemap(List<String> pageUrls, List<String> sitemapUrls) {
    }

    private SitemapParser() {
    }

    public static Sitemap parse(String xml) {
        Document doc = Jsoup.parse(xml, "", Parser.xmlParser());
        List<String> pages = new ArrayList<>();
        List<String> nested = new ArrayList<>();
        for (Element loc : doc.select("loc")) {
            Element parent = loc.parent();
            String value = loc.text().trim();
            if (value.isEmpty() || parent == null) {
                continue;
            }
            String parentName = parent.normalName();
            if (parentName.endsWith("sitemap")) {
                nested.add(value);
            } else if (parentName.endsWith("url")) {
                pages.add(value);
            }
        }
        return new Sitemap(pages, nested);
    }
}
