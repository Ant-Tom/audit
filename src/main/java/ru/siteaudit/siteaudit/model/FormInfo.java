package ru.siteaudit.siteaudit.model;

import java.util.List;

/**
 * Форма на странице.
 *
 * @param action     абсолютный URL отправки (для «форм без тега form» — пустая строка)
 * @param method     HTTP-метод в верхнем регистре; для «форм без тега form» — пустая строка
 * @param fields     поля ввода (кроме чекбоксов, скрытых полей и кнопок)
 * @param checkboxes чекбоксы с их подписями
 */
public record FormInfo(String action, String method, List<FieldInfo> fields, List<CheckboxInfo> checkboxes) {
    public FormInfo {
        action = action == null ? "" : action;
        method = method == null ? "" : method;
        fields = List.copyOf(fields);
        checkboxes = List.copyOf(checkboxes);
    }

    /** Форма собирает данные, которые обычно являются персональными (имя, телефон, email). */
    public boolean collectsPersonalData() {
        return fields.stream().anyMatch(FieldInfo::personalData);
    }
}
