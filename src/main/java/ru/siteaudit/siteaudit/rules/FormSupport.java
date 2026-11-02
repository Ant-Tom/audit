package ru.siteaudit.siteaudit.rules;

import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.FormInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Общие для правил о формах операции: поиск чекбокса согласия, группировка одинаковых форм. */
public final class FormSupport {

    private static final Pattern CONSENT_VERB = Pattern.compile("согла[сш]|даю|принима|consent|agree");
    private static final Pattern CONSENT_OBJECT = Pattern.compile(
            "персональн|обработк|данн|политик|конфиденц|оферт|услови|соглашени|privacy|personal|policy|terms");

    private FormSupport() {
    }

    /** Чекбокс, подпись или ссылки которого говорят о согласии / принятии документов. */
    public static boolean isConsentCheckbox(CheckboxInfo checkbox) {
        String text = checkbox.labelText().toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (CONSENT_VERB.matcher(text).find() && CONSENT_OBJECT.matcher(text).find()) {
            return true;
        }
        return checkbox.links().stream().anyMatch(href -> LegalDocClassifier.isLegal("", href))
                && CONSENT_VERB.matcher(text).find();
    }

    public static List<CheckboxInfo> consentCheckboxes(FormInfo form) {
        return form.checkboxes().stream().filter(FormSupport::isConsentCheckbox).toList();
    }

    /**
     * Форма входа (логин + пароль) — данные собираются для исполнения договора с уже зарегистрированным
     * пользователем; согласие там обычно не запрашивают, поэтому такие формы не проверяем.
     */
    public static boolean isLoginForm(FormInfo form) {
        boolean hasPassword = form.fields().stream().anyMatch(f -> "password".equals(f.type()));
        return hasPassword && form.fields().size() <= 3;
    }

    /** Форма, к которой применимы правила о согласии на обработку ПДн. */
    public static boolean collectsPersonalData(FormInfo form) {
        return form.collectsPersonalData() && !isLoginForm(form);
    }

    /** Одна и та же форма (например, «обратный звонок» в шапке) повторяется на всех страницах. */
    public record FormOccurrences(FormInfo form, List<String> pageUrls) {
        public String firstPage() {
            return pageUrls.getFirst();
        }
    }

    /** Группирует одинаковые формы со всех страниц, сохраняя порядок обнаружения. */
    public static List<FormOccurrences> distinctForms(List<PageSnapshot> pages) {
        Map<String, FormInfo> forms = new LinkedHashMap<>();
        Map<String, List<String>> urls = new LinkedHashMap<>();
        for (PageSnapshot page : pages) {
            for (FormInfo form : page.forms()) {
                String key = signature(form);
                forms.putIfAbsent(key, form);
                List<String> list = urls.computeIfAbsent(key, k -> new ArrayList<>());
                if (!list.contains(page.url())) {
                    list.add(page.url());
                }
            }
        }
        return forms.entrySet().stream()
                .map(e -> new FormOccurrences(e.getValue(), List.copyOf(urls.get(e.getKey()))))
                .toList();
    }

    private static String signature(FormInfo form) {
        String fields = form.fields().stream().map(f -> f.name() + ":" + f.type()).collect(Collectors.joining(","));
        String boxes = form.checkboxes().stream()
                .map(c -> c.name() + ":" + c.checked() + ":" + c.labelText())
                .collect(Collectors.joining(","));
        // action не учитываем целиком: у одной и той же формы он часто содержит адрес текущей страницы
        return form.method() + "|" + fields + "|" + boxes;
    }
}
