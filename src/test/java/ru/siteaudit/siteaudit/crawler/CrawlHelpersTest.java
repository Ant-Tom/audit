package ru.siteaudit.siteaudit.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlHelpersTest {

    @Test
    void normalizesUrls() {
        assertThat(UrlUtils.normalize("HTTPS://Site.Example:443/a/../b#top")).contains("https://site.example/b");
        assertThat(UrlUtils.normalize("https://site.example")).contains("https://site.example/");
        assertThat(UrlUtils.normalize("mailto:a@b.c")).isEmpty();
        assertThat(UrlUtils.normalize("javascript:void(0)")).isEmpty();
    }

    @Test
    void sameSiteIgnoresWww() {
        assertThat(UrlUtils.sameSite("https://www.site.example/x", "site.example")).isTrue();
        assertThat(UrlUtils.sameSite("https://other.example/x", "site.example")).isFalse();
    }

    @Test
    void detectsFiles() {
        assertThat(UrlUtils.looksLikeFile("https://s.example/docs/policy.pdf")).isTrue();
        assertThat(UrlUtils.looksLikeFile("https://s.example/contacts/")).isFalse();
    }

    @Test
    void prioritizesFormPagesAndLegalDocs() {
        int contacts = LinkPrioritizer.score("Контакты", "https://s.example/contacts");
        int blog = LinkPrioritizer.score("Блог", "https://s.example/blog/post-1");
        int plain = LinkPrioritizer.score("Портфолио", "https://s.example/portfolio");
        assertThat(contacts).isGreaterThan(plain).isGreaterThan(blog);
        assertThat(LinkPrioritizer.score("Политика конфиденциальности", "https://s.example/privacy"))
                .isEqualTo(LinkPrioritizer.LEGAL);
    }

    @Test
    void parsesSitemapAndIndex() {
        var urlset = SitemapParser.parse("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url><loc>https://s.example/</loc></url>
                  <url><loc>https://s.example/contacts</loc></url>
                </urlset>""");
        assertThat(urlset.pageUrls()).containsExactly("https://s.example/", "https://s.example/contacts");
        var index = SitemapParser.parse("""
                <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <sitemap><loc>https://s.example/sitemap-pages.xml</loc></sitemap>
                </sitemapindex>""");
        assertThat(index.sitemapUrls()).containsExactly("https://s.example/sitemap-pages.xml");
        assertThat(index.pageUrls()).isEmpty();
    }
}
