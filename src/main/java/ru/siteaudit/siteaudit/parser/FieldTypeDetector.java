package ru.siteaudit.siteaudit.parser;

import ru.siteaudit.siteaudit.model.FieldInfo;

import java.util.Locale;
import java.util.regex.Pattern;

/** Определяет, какие данные собирает поле: имя, телефон, email — по type, name, id, placeholder, label. */
public final class FieldTypeDetector {

    private static final Pattern EMAIL = Pattern.compile("e-?mail|почт|мейл|майл");
    private static final Pattern PHONE = Pattern.compile("phone|(^|[^a-z])tel([^a-z]|$)|телефон|мобильн|whatsapp|номер");
    private static final Pattern NAME = Pattern.compile(
            "(^|[^a-z])(name|fio|fullname|firstname|lastname|surname|first[-_ ]name|last[-_ ]name)([^a-z]|$)"
                    + "|фио|(^|[^а-я])имя|фамили|отчеств|как к вам обращаться|как вас зовут");
    private static final Pattern NOT_PERSON_NAME = Pattern.compile(
            "company|organi[sz]ation|компан|организац|назван|product|товар|file|city|город");

    private FieldTypeDetector() {
    }

    /**
     * @return {@link FieldInfo#TYPE_EMAIL}, {@link FieldInfo#TYPE_PHONE}, {@link FieldInfo#TYPE_NAME}
     * или исходный тип поля
     */
    public static String detect(String htmlType, String... hints) {
        String type = htmlType == null || htmlType.isBlank() ? "text" : htmlType.toLowerCase(Locale.ROOT);
        if (type.equals("email")) {
            return FieldInfo.TYPE_EMAIL;
        }
        if (type.equals("tel")) {
            return FieldInfo.TYPE_PHONE;
        }
        if (!type.equals("text") && !type.equals("textarea") && !type.equals("search")) {
            return type; // number, date, select и т.п. — не угадываем
        }
        String haystack = String.join(" ", java.util.Arrays.stream(hints)
                .map(h -> h == null ? "" : h.toLowerCase(Locale.ROOT).replace('ё', 'е'))
                .toList());
        if (EMAIL.matcher(haystack).find()) {
            return FieldInfo.TYPE_EMAIL;
        }
        if (PHONE.matcher(haystack).find()) {
            return FieldInfo.TYPE_PHONE;
        }
        if (!type.equals("textarea") && NAME.matcher(haystack).find() && !NOT_PERSON_NAME.matcher(haystack).find()) {
            return FieldInfo.TYPE_NAME;
        }
        return type;
    }
}
