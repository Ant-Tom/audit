package ru.siteaudit.siteaudit.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.crawler.UrlUtils;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.FieldInfo;
import ru.siteaudit.siteaudit.model.FormInfo;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.RawPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Превращает отрендеренный HTML в {@link PageSnapshot}. Не зависит от браузера —
 * тестируется на HTML-фикстурах.
 */
@Component
public class PageParser {

    /** Поля, которые не являются вводом данных пользователем. */
    private static final Set<String> NON_DATA_INPUT_TYPES = Set.of(
            "hidden", "submit", "button", "image", "reset", "checkbox", "radio", "file", "range", "color");
    private static final String DATA_FIELDS = "input, textarea, select";
    private static final String SUBMIT_CONTROLS =
            "button, input[type=submit], input[type=button], input[type=image], [role=button], a[class*=btn], "
                    + "div[class*=btn], span[class*=btn], a[class*=submit], div[class*=submit], [onclick]";
    private static final Pattern SUBMIT_TEXT = Pattern.compile(
            "отправ|заказ|оформ|получ|запис|подписа|звонок|перезвон|заявк|купить|send|submit|order|subscribe");
    /** Насколько высоко подниматься от поля в поисках кнопки отправки для «формы без тега form». */
    private static final int PSEUDO_FORM_MAX_DEPTH = 6;
    private static final int MAX_CHECKBOX_LABEL = 1000;

    public PageSnapshot parse(RawPage raw) {
        Document doc = Jsoup.parse(raw.renderedHtml(), raw.url());
        String siteHost = UrlUtils.siteHost(raw.url());

        List<FormInfo> forms = new ArrayList<>();
        for (Element form : doc.select("form")) {
            String action = form.hasAttr("action") && !form.attr("action").isBlank()
                    ? form.absUrl("action") : raw.url();
            String method = form.attr("method").isBlank() ? "GET" : form.attr("method").toUpperCase(Locale.ROOT);
            forms.add(buildForm(doc, form, action, method));
        }
        for (Element container : findPseudoFormContainers(doc)) {
            forms.add(buildForm(doc, container, "", ""));
        }
        forms.removeIf(f -> f.fields().isEmpty() && f.checkboxes().isEmpty());

        return new PageSnapshot(
                raw.url(),
                raw.requestsBeforeInteraction(),
                forms,
                collectLinks(doc, siteHost, true),
                collectLinks(doc, siteHost, false),
                CookieBannerDetector.hasCookieBanner(doc),
                visibleText(doc));
    }

    // ---------- формы ----------

    private FormInfo buildForm(Document doc, Element container, String action, String method) {
        List<FieldInfo> fields = new ArrayList<>();
        List<CheckboxInfo> checkboxes = new ArrayList<>();
        for (Element input : container.select(DATA_FIELDS)) {
            String type = fieldHtmlType(input);
            if (type.equals("checkbox")) {
                checkboxes.add(buildCheckbox(doc, input));
            } else if (!NON_DATA_INPUT_TYPES.contains(type)) {
                fields.add(buildField(doc, input, type));
            }
        }
        return new FormInfo(action, method, fields, checkboxes);
    }

    private static String fieldHtmlType(Element input) {
        return switch (input.normalName()) {
            case "textarea" -> "textarea";
            case "select" -> "select";
            default -> input.attr("type").isBlank() ? "text" : input.attr("type").toLowerCase(Locale.ROOT);
        };
    }

    private FieldInfo buildField(Document doc, Element input, String htmlType) {
        String label = labelText(doc, input);
        String placeholder = input.attr("placeholder");
        String ariaLabel = input.attr("aria-label");
        String detected = FieldTypeDetector.detect(htmlType,
                input.attr("name"), input.id(), placeholder, label, ariaLabel, input.attr("autocomplete"));
        String shownLabel = firstNonBlank(label, placeholder, ariaLabel, input.attr("title"));
        return new FieldInfo(nameOf(input), detected, shownLabel);
    }

    private CheckboxInfo buildCheckbox(Document doc, Element input) {
        Element labelEl = labelElement(doc, input);
        String text;
        List<Element> linkSources = new ArrayList<>();
        if (labelEl != null) {
            text = labelEl.text();
            linkSources.add(labelEl);
        } else {
            // Частый случай на конструкторах: <input type=checkbox><span>Я согласен … <a>…</a></span> без <label>.
            // Берём соседей справа до следующего поля или кнопки.
            StringBuilder sb = new StringBuilder();
            for (Node sibling = input.nextSibling(); sibling != null; sibling = sibling.nextSibling()) {
                if (sibling instanceof TextNode textNode) {
                    sb.append(textNode.text());
                } else if (sibling instanceof Element element) {
                    if (isControl(element)) {
                        break;
                    }
                    sb.append(' ').append(element.text());
                    linkSources.add(element);
                }
                if (sb.length() > MAX_CHECKBOX_LABEL) {
                    break;
                }
            }
            text = sb.toString().replaceAll("\\s+", " ").trim();
            if (text.isEmpty()) {
                text = input.attr("aria-label");
            }
        }
        List<String> links = new ArrayList<>();
        for (Element source : linkSources) {
            for (Element a : source.select("a[href]")) {
                String href = a.absUrl("href");
                if (!href.isBlank()) {
                    links.add(href);
                }
            }
        }
        return new CheckboxInfo(nameOf(input), input.hasAttr("checked"), text, links);
    }

    /** Элемент сам является полем/кнопкой или содержит их — граница подписи чекбокса. */
    private static boolean isControl(Element el) {
        return el.is(DATA_FIELDS) || el.is(SUBMIT_CONTROLS)
                || !el.select(DATA_FIELDS).isEmpty() || !el.select("button, input, [class*=btn]").isEmpty();
    }

    /** {@code <label for="id">} или label, внутри которого лежит поле. */
    private static Element labelElement(Document doc, Element input) {
        if (!input.id().isBlank()) {
            Elements byFor = doc.getElementsByAttributeValue("for", input.id());
            if (!byFor.isEmpty()) {
                return byFor.first();
            }
        }
        for (Element parent : input.parents()) {
            if (parent.normalName().equals("label")) {
                return parent;
            }
            if (parent.normalName().equals("form")) {
                break;
            }
        }
        return null;
    }

    private static String labelText(Document doc, Element input) {
        Element label = labelElement(doc, input);
        return label == null ? "" : label.text();
    }

    private static String nameOf(Element input) {
        return firstNonBlank(input.attr("name"), input.id(), "");
    }

    /**
     * «Формы без тега form»: поля ввода вне {@code <form>}, у которых в ближайшем общем контейнере есть кнопка
     * отправки. Для каждого такого поля поднимаемся вверх до первого предка, содержащего кнопку, — это и есть
     * контейнер формы.
     *
     * <p>TODO: эвристика. Возможные ошибки: несколько форм в одном контейнере сольются в одну; кнопка-ссылка
     * без характерного класса/текста не будет найдена. Точнее можно определять по обработчикам событий в
     * браузере (getEventListeners доступен только в CDP) — отложено.
     */
    private List<Element> findPseudoFormContainers(Document doc) {
        Map<Element, Boolean> containers = new LinkedHashMap<>();
        for (Element input : doc.select(DATA_FIELDS)) {
            if (insideForm(input) || NON_DATA_INPUT_TYPES.contains(fieldHtmlType(input))) {
                continue;
            }
            Element current = input.parent();
            for (int depth = 0; current != null && depth < PSEUDO_FORM_MAX_DEPTH; depth++) {
                String tag = current.normalName();
                if (tag.equals("body") || tag.equals("html")) {
                    break;
                }
                if (hasSubmitControl(current)) {
                    containers.put(current, true);
                    break;
                }
                current = current.parent();
            }
        }
        // Если один контейнер вложен в другой — оставляем внутренний (ближайший к полям)
        List<Element> all = List.copyOf(containers.keySet());
        return all.stream()
                .filter(outer -> all.stream().noneMatch(inner -> inner != outer && inner.parents().contains(outer)))
                .toList();
    }

    private static boolean insideForm(Element el) {
        return el.parents().stream().anyMatch(p -> p.normalName().equals("form"));
    }

    private static boolean hasSubmitControl(Element container) {
        for (Element candidate : container.select(SUBMIT_CONTROLS)) {
            if (candidate.normalName().equals("button") || candidate.normalName().equals("input")) {
                if (!candidate.attr("type").equalsIgnoreCase("reset")) {
                    return true;
                }
            }
            String text = (candidate.text() + " " + candidate.className()).toLowerCase(Locale.ROOT);
            if (SUBMIT_TEXT.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    // ---------- ссылки и текст ----------

    private static List<LinkInfo> collectLinks(Document doc, String siteHost, boolean legal) {
        Set<String> seen = new LinkedHashSet<>();
        List<LinkInfo> links = new ArrayList<>();
        for (Element a : doc.select("a[href]")) {
            String href = a.absUrl("href");
            if (!href.startsWith("http")) {
                continue;
            }
            String text = firstNonBlank(a.text(), a.attr("title"), a.attr("aria-label"));
            boolean matches = legal
                    ? LegalDocClassifier.isLegal(text, href)
                    : !UrlUtils.sameSite(href, siteHost);
            if (matches && seen.add(href)) {
                links.add(new LinkInfo(text, href));
            }
        }
        return links;
    }

    private static String visibleText(Document doc) {
        if (doc.body() == null) {
            return "";
        }
        Element body = doc.body().clone();
        body.select("script, style, noscript, template, svg, [hidden], [aria-hidden=true]").remove();
        return body.text();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
