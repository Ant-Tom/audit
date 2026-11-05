package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.ArrayList;
import java.util.List;

/** Форма собирает имя / телефон / email, но в ней нет чекбокса согласия на обработку ПДн. */
@Component
public class PdFormConsentRule implements Rule {

    public static final String ID = "pd-form-consent";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        List<Finding> findings = new ArrayList<>();
        for (FormSupport.FormOccurrences occurrence : FormSupport.distinctForms(pages)) {
            if (!FormSupport.collectsPersonalData(occurrence.form())
                    || !FormSupport.consentCheckboxes(occurrence.form()).isEmpty()) {
                continue;
            }
            findings.add(new Finding(
                    ID,
                    Severity.CRITICAL,
                    "Форма собирает персональные данные без отметки о согласии",
                    "Форма просит указать имя, телефон или email, но в ней нет отдельной галочки "
                            + "«Я даю согласие на обработку персональных данных». Без согласия (или другого законного "
                            + "основания) обработка персональных данных может быть признана незаконной.",
                    occurrence.firstPage(),
                    Evidence.describeForm(occurrence.form()) + ". Страницы с этой формой: "
                            + Evidence.list(occurrence.pageUrls()),
                    "pd-consent-required",
                    "Рекомендуем добавить в форму неотмеченную галочку согласия со ссылкой на текст согласия "
                            + "и не давать отправить форму без неё. Если согласие собирается иначе "
                            + "(например, на следующем шаге), проверьте этот шаг вручную."));
        }
        return findings;
    }
}
