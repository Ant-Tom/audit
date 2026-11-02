package ru.siteaudit.siteaudit.model;

/**
 * Поле формы.
 *
 * @param name  атрибут name (или id, если name нет)
 * @param type  тип данных, определённый парсером: {@link #TYPE_NAME}, {@link #TYPE_PHONE},
 *              {@link #TYPE_EMAIL} или исходный HTML-тип поля (text, textarea, select и т.п.)
 * @param label подпись поля: label, placeholder или aria-label
 */
public record FieldInfo(String name, String type, String label) {
    public static final String TYPE_NAME = "name";
    public static final String TYPE_PHONE = "phone";
    public static final String TYPE_EMAIL = "email";

    public boolean personalData() {
        return TYPE_NAME.equals(type) || TYPE_PHONE.equals(type) || TYPE_EMAIL.equals(type);
    }
}
