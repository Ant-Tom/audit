package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier.DocKind;

import java.util.List;

/** На проверенных страницах нет ссылки на политику обработки персональных данных. */
@Component
public class PdPolicyLinkRule implements Rule {

    public static final String ID = "pd-policy-link";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        if (pages.isEmpty()) {
            return List.of();
        }
        boolean found = pages.stream()
                .flatMap(p -> p.legalLinks().stream())
                .anyMatch(l -> LegalDocClassifier.is(DocKind.POLICY, l.text(), l.href()));
        if (found) {
            return List.of();
        }
        List<LinkInfo> consentLinks = pages.stream()
                .flatMap(p -> p.legalLinks().stream())
                .filter(l -> LegalDocClassifier.is(DocKind.CONSENT, l.text(), l.href()))
                .toList();
        String evidence = "Проверено страниц: " + pages.size()
                + ". Не найдено ни одной ссылки, в тексте или адресе которой есть слова «политика», «персональные данные», «конфиденциальность»."
                + (consentLinks.isEmpty() ? "" : " Найдены только ссылки на согласие: "
                + Evidence.list(consentLinks.stream().map(LinkInfo::href).toList()) + ".");
        return List.of(new Finding(
                ID,
                Severity.CRITICAL,
                "Не найдена ссылка на политику обработки персональных данных",
                "Мы не нашли на сайте ссылку на документ о политике обработки персональных данных. "
                        + "Если сайт собирает данные посетителей (формы, заказы, счётчики), такой документ должен быть "
                        + "опубликован на сайте и доступен любому посетителю.",
                pages.getFirst().url(),
                evidence,
                "pd-policy-publication",
                "Рекомендуем проверить, опубликована ли политика обработки персональных данных, и разместить ссылку "
                        + "на неё в подвале каждой страницы и рядом с формами. Если ссылка есть, но мы её не нашли "
                        + "(например, она в картинке или открывается скриптом), сделайте её обычной текстовой ссылкой."));
    }
}
