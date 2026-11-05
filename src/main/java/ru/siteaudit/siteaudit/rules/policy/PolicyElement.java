package ru.siteaudit.siteaudit.rules.policy;

/** Элементы, которые ожидаются в тексте политики обработки персональных данных. */
public enum PolicyElement {
    OPERATOR_NAME("наименование оператора (организации или ИП)"),
    OPERATOR_ID("ИНН или ОГРН оператора"),
    PURPOSES("цели обработки персональных данных"),
    DATA_CATEGORIES("перечень обрабатываемых персональных данных"),
    RETENTION("сроки обработки и хранения"),
    SUBJECT_RIGHTS("права субъекта персональных данных"),
    CONSENT_WITHDRAWAL("порядок отзыва согласия");

    private final String description;

    PolicyElement(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
