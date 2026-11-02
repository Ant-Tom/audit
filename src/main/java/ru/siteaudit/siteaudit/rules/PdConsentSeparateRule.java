package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier.DocKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ссылка у чекбокса согласия ведёт на политику или оферту, а не на отдельный документ согласия.
 * Чекбоксы без ссылок и со ссылками, тип которых определить не удалось, не оцениваются.
 */
@Component
public class PdConsentSeparateRule implements Rule {

    public static final String ID = "pd-consent-separate";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        // В CheckboxInfo.links только адреса; текст ссылки берём из legalLinks страниц
        Map<String, String> linkTexts = new HashMap<>();
        pages.forEach(p -> p.legalLinks().forEach(l -> linkTexts.putIfAbsent(l.href(), l.text())));

        List<Finding> findings = new ArrayList<>();
        for (FormSupport.FormOccurrences occurrence : FormSupport.distinctForms(pages)) {
            if (!FormSupport.collectsPersonalData(occurrence.form())) {
                continue;
            }
            for (CheckboxInfo checkbox : FormSupport.consentCheckboxes(occurrence.form())) {
                if (checkbox.links().isEmpty()) {
                    continue;
                }
                Set<DocKind> kinds = EnumSet.noneOf(DocKind.class);
                for (String href : checkbox.links()) {
                    kinds.addAll(LegalDocClassifier.classify(linkTexts.getOrDefault(href, ""), href));
                }
                boolean pointsToOtherDocs = kinds.contains(DocKind.POLICY) || kinds.contains(DocKind.OFFER);
                if (kinds.contains(DocKind.CONSENT) || !pointsToOtherDocs) {
                    continue;
                }
                List<String> described = checkbox.links().stream()
                        .map(href -> {
                            String text = linkTexts.getOrDefault(href, "");
                            return text.isBlank() ? href : "«" + text + "» (" + href + ")";
                        })
                        .toList();
                findings.add(new Finding(
                        ID,
                        Severity.WARNING,
                        "Согласие на обработку данных не оформлено отдельным документом",
                        "Галочка согласия ссылается на политику или оферту, а отдельного документа «Согласие на "
                                + "обработку персональных данных» мы не нашли. Согласие должно оформляться отдельно "
                                + "от других документов — см. описание нормы ниже.",
                        occurrence.firstPage(),
                        "Подпись: «" + checkbox.labelText() + "». Ссылки: " + String.join(", ", described)
                                + ". Страницы: " + Evidence.list(occurrence.pageUrls()),
                        "pd-consent-separate",
                        "Рекомендуем подготовить отдельный текст согласия на обработку персональных данных и "
                                + "ссылаться на него из галочки; ссылку на политику можно оставить рядом."));
            }
        }
        return findings;
    }

}
