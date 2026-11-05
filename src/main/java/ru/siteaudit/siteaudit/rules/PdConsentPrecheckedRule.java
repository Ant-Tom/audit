package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.ArrayList;
import java.util.List;

/** Чекбокс согласия отмечен заранее. */
@Component
public class PdConsentPrecheckedRule implements Rule {

    public static final String ID = "pd-consent-prechecked";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        List<Finding> findings = new ArrayList<>();
        for (FormSupport.FormOccurrences occurrence : FormSupport.distinctForms(pages)) {
            for (CheckboxInfo checkbox : FormSupport.consentCheckboxes(occurrence.form())) {
                if (!checkbox.checked()) {
                    continue;
                }
                findings.add(new Finding(
                        ID,
                        Severity.CRITICAL,
                        "Галочка согласия отмечена заранее",
                        "Галочка согласия на обработку данных уже стоит, когда посетитель открывает страницу. "
                                + "Такое согласие может не считаться сознательным и однозначным: посетитель его "
                                + "фактически не давал.",
                        occurrence.firstPage(),
                        "Чекбокс «" + checkbox.labelText() + "»"
                                + (checkbox.name().isEmpty() ? "" : " (name=\"" + checkbox.name() + "\")")
                                + " отмечен при загрузке. " + Evidence.describeForm(occurrence.form())
                                + ". Страницы: " + Evidence.list(occurrence.pageUrls()),
                        "pd-consent-form",
                        "Рекомендуем убрать отметку по умолчанию: посетитель должен поставить галочку сам."));
            }
        }
        return findings;
    }
}
