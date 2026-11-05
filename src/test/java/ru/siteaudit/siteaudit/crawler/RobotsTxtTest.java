package ru.siteaudit.siteaudit.crawler;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtTest {

    private static final String ROBOTS = """
            User-agent: Yandex
            Disallow: /

            User-agent: *
            Disallow: /admin/
            Disallow: /*?sort=
            Disallow: /*.php$
            Allow: /admin/public/
            Crawl-delay: 2.5
            Sitemap: https://site.example/sitemap_index.xml
            """;

    @Test
    void usesWildcardGroupWhenNoSpecificOne() {
        RobotsTxt robots = RobotsTxt.parse(ROBOTS, "SiteAuditBot");
        assertThat(robots.isAllowed("/")).isTrue();
        assertThat(robots.isAllowed("/admin/users")).isFalse();
        assertThat(robots.isAllowed("/admin/public/page")).isTrue();
        assertThat(robots.isAllowed("/catalog?sort=price")).isFalse();
        assertThat(robots.isAllowed("/index.php")).isFalse();
        assertThat(robots.isAllowed("/index.php?x=1")).isTrue();
        assertThat(robots.crawlDelay()).contains(Duration.ofMillis(2500));
        assertThat(robots.sitemaps()).containsExactly("https://site.example/sitemap_index.xml");
    }

    @Test
    void specificGroupWins() {
        String robots = "User-agent: *\nDisallow: /\n\nUser-agent: SiteAuditBot\nDisallow: /private\n";
        RobotsTxt parsed = RobotsTxt.parse(robots, "SiteAuditBot");
        assertThat(parsed.isAllowed("/")).isTrue();
        assertThat(parsed.isAllowed("/private/x")).isFalse();
    }

    @Test
    void emptyDisallowAllowsEverything() {
        assertThat(RobotsTxt.parse("User-agent: *\nDisallow:\n", "SiteAuditBot").isAllowed("/x")).isTrue();
    }
}
