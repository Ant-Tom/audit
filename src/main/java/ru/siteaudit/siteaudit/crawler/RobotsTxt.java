package ru.siteaudit.siteaudit.crawler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Минимальный разбор robots.txt по RFC 9309: группы User-agent, Allow/Disallow с поддержкой {@code *} и
 * {@code $}, побеждает самое длинное совпадение, при равенстве — Allow. Дополнительно читаются
 * Crawl-delay и Sitemap.
 */
public final class RobotsTxt {

    private record RulePattern(boolean allow, String raw, Pattern regex) {
    }

    private final List<RulePattern> rules;
    private final Duration crawlDelay;
    private final List<String> sitemaps;

    private RobotsTxt(List<RulePattern> rules, Duration crawlDelay, List<String> sitemaps) {
        this.rules = rules;
        this.crawlDelay = crawlDelay;
        this.sitemaps = sitemaps;
    }

    public static RobotsTxt allowAll() {
        return new RobotsTxt(List.of(), null, List.of());
    }

    /**
     * @param content содержимое robots.txt
     * @param agent   токен нашего бота, например {@code SiteAuditBot}
     */
    public static RobotsTxt parse(String content, String agent) {
        String agentLower = agent.toLowerCase(Locale.ROOT);
        List<String> sitemaps = new ArrayList<>();

        // Собираем группы: список user-agent + список строк правил
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasAgent = false;
        for (String rawLine : content.split("\\R")) {
            String line = rawLine.replaceFirst("#.*$", "").trim();
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "user-agent" -> {
                    if (current == null || !lastWasAgent) {
                        current = new Group();
                        groups.add(current);
                    }
                    current.agents.add(value.toLowerCase(Locale.ROOT));
                    lastWasAgent = true;
                }
                case "allow", "disallow", "crawl-delay" -> {
                    if (current != null) {
                        current.lines.add(new String[]{key, value});
                    }
                    lastWasAgent = false;
                }
                case "sitemap" -> {
                    if (!value.isEmpty()) {
                        sitemaps.add(value);
                    }
                }
                default -> lastWasAgent = false;
            }
        }

        List<Group> matching = groups.stream()
                .filter(g -> g.agents.stream().anyMatch(a -> !a.equals("*") && agentLower.contains(a)))
                .toList();
        if (matching.isEmpty()) {
            matching = groups.stream().filter(g -> g.agents.contains("*")).toList();
        }

        List<RulePattern> rules = new ArrayList<>();
        Duration delay = null;
        for (Group g : matching) {
            for (String[] kv : g.lines) {
                switch (kv[0]) {
                    case "allow" -> {
                        if (!kv[1].isEmpty()) {
                            rules.add(new RulePattern(true, kv[1], toRegex(kv[1])));
                        }
                    }
                    case "disallow" -> {
                        // Пустой Disallow означает «можно всё»
                        if (!kv[1].isEmpty()) {
                            rules.add(new RulePattern(false, kv[1], toRegex(kv[1])));
                        }
                    }
                    case "crawl-delay" -> {
                        try {
                            delay = Duration.ofMillis((long) (Double.parseDouble(kv[1]) * 1000));
                        } catch (NumberFormatException ignored) {
                            // некорректное значение игнорируем
                        }
                    }
                    default -> {
                    }
                }
            }
        }
        return new RobotsTxt(List.copyOf(rules), delay, List.copyOf(sitemaps));
    }

    private static Pattern toRegex(String pattern) {
        StringBuilder sb = new StringBuilder("^");
        boolean anchored = pattern.endsWith("$");
        String body = anchored ? pattern.substring(0, pattern.length() - 1) : pattern;
        for (String part : body.split("\\*", -1)) {
            if (sb.length() > 1) {
                sb.append(".*");
            }
            sb.append(Pattern.quote(part));
        }
        if (anchored) {
            sb.append('$');
        }
        return Pattern.compile(sb.toString());
    }

    /** @param pathAndQuery путь и query-строка, например {@code /catalog?page=2} */
    public boolean isAllowed(String pathAndQuery) {
        RulePattern best = null;
        for (RulePattern rule : rules) {
            if (rule.regex.matcher(pathAndQuery).find()) {
                if (best == null
                        || rule.raw.length() > best.raw.length()
                        || (rule.raw.length() == best.raw.length() && rule.allow)) {
                    best = rule;
                }
            }
        }
        return best == null || best.allow;
    }

    public Optional<Duration> crawlDelay() {
        return Optional.ofNullable(crawlDelay);
    }

    public List<String> sitemaps() {
        return sitemaps;
    }

    private static final class Group {
        final List<String> agents = new ArrayList<>();
        final List<String[]> lines = new ArrayList<>();
    }
}
