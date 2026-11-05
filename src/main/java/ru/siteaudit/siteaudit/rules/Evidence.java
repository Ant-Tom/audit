package ru.siteaudit.siteaudit.rules;

import ru.siteaudit.siteaudit.model.FieldInfo;
import ru.siteaudit.siteaudit.model.FormInfo;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/** Форматирование доказательств для отчёта. */
public final class Evidence {

    private static final int MAX_LISTED = 5;

    private Evidence() {
    }

    /** «a, b, c и ещё 4» */
    public static String list(Collection<String> items) {
        List<String> distinct = items.stream().distinct().toList();
        String head = String.join(", ", distinct.subList(0, Math.min(MAX_LISTED, distinct.size())));
        int rest = distinct.size() - MAX_LISTED;
        return rest > 0 ? head + " и ещё " + rest : head;
    }

    /** Описание формы, понятное человеку: какие поля и куда отправляется. */
    public static String describeForm(FormInfo form) {
        String fields = form.fields().stream()
                .map(f -> {
                    String label = f.label().isBlank() ? f.name() : f.label();
                    String kind = humanType(f);
                    return kind.isEmpty() ? "«" + label + "»" : "«" + label + "» (" + kind + ")";
                })
                .collect(Collectors.joining(", "));
        String target = form.action().isEmpty()
                ? "форма без тега <form>, отправка скриптом"
                : "отправка: " + (form.method().isEmpty() ? "" : form.method() + " ") + form.action();
        return "Поля: " + (fields.isEmpty() ? "—" : fields) + "; " + target;
    }

    private static String humanType(FieldInfo field) {
        return switch (field.type()) {
            case FieldInfo.TYPE_NAME -> "имя";
            case FieldInfo.TYPE_PHONE -> "телефон";
            case FieldInfo.TYPE_EMAIL -> "email";
            default -> "";
        };
    }
}
