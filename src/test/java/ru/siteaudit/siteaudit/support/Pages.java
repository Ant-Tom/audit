package ru.siteaudit.siteaudit.support;

import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.FieldInfo;
import ru.siteaudit.siteaudit.model.FormInfo;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;

import java.util.List;

/** Конструкторы PageSnapshot для тестов правил. */
public final class Pages {

    private Pages() {
    }

    public static PageSnapshot page(String url) {
        return new PageSnapshot(url, List.of(), List.of(), List.of(), List.of(), false, "");
    }

    public static PageSnapshot withForms(String url, FormInfo... forms) {
        return new PageSnapshot(url, List.of(), List.of(forms), List.of(), List.of(), false, "");
    }

    public static PageSnapshot withLinks(String url, LinkInfo... links) {
        return new PageSnapshot(url, List.of(), List.of(), List.of(links), List.of(), false, "");
    }

    public static PageSnapshot withRequests(String url, String... requests) {
        return new PageSnapshot(url, List.of(requests), List.of(), List.of(), List.of(), false, "");
    }

    public static FormInfo form(String action, List<FieldInfo> fields, CheckboxInfo... checkboxes) {
        return new FormInfo(action, "POST", fields, List.of(checkboxes));
    }

    public static List<FieldInfo> nameAndPhone() {
        return List.of(new FieldInfo("name", FieldInfo.TYPE_NAME, "Имя"),
                new FieldInfo("phone", FieldInfo.TYPE_PHONE, "Телефон"));
    }

    public static CheckboxInfo consent(boolean checked, String... links) {
        return new CheckboxInfo("agree", checked, "Я даю согласие на обработку персональных данных", List.of(links));
    }
}
