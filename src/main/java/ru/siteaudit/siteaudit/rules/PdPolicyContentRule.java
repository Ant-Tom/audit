package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.crawler.UrlUtils;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier.DocKind;
import ru.siteaudit.siteaudit.rules.policy.PolicyAnalysis;
import ru.siteaudit.siteaudit.rules.policy.PolicyAnalyzer;
import ru.siteaudit.siteaudit.rules.policy.PolicyElement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * В тексте политики не найдены обязательные элементы. Проверяются страницы, на которые ведут ссылки
 * на политику. Если такие страницы не удалось открыть (PDF, другой домен) — INFO о том, что текст не проверен.
 */
@Component
public class PdPolicyContentRule implements Rule {

    public static final String ID = "pd-policy-content";

    private final PolicyAnalyzer analyzer;

    public PdPolicyContentRule(PolicyAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        Set<String> policyUrls = new LinkedHashSet<>();
        for (PageSnapshot page : pages) {
            for (LinkInfo link : page.legalLinks()) {
                if (LegalDocClassifier.is(DocKind.POLICY, link.text(), link.href())) {
                    policyUrls.add(UrlUtils.normalize(link.href()).orElse(link.href()));
                }
            }
        }
        if (policyUrls.isEmpty()) {
            return List.of(); // отсутствие ссылки — забота pd-policy-link
        }
        List<PageSnapshot> policyPages = pages.stream().filter(p -> policyUrls.contains(p.url())).toList();
        if (policyPages.isEmpty()) {
            return List.of(new Finding(
                    ID,
                    Severity.INFO,
                    "Текст политики не проверен",
                    "Ссылка на политику обработки персональных данных есть, но открыть и прочитать документ "
                            + "автоматически не удалось (например, это PDF-файл или страница на другом сайте).",
                    pages.getFirst().url(),
                    "Ссылки на политику: " + Evidence.list(policyUrls),
                    "pd-policy-content",
                    "Рекомендуем проверить содержание политики вручную или разместить её текст на обычной "
                            + "странице сайта — так её удобнее читать и посетителям."));
        }
        List<Finding> findings = new ArrayList<>();
        for (PageSnapshot policy : policyPages) {
            PolicyAnalysis analysis = analyzer.analyze(policy.visibleText());
            if (analysis.missing().isEmpty()) {
                continue;
            }
            String missing = analysis.missing().stream().map(PolicyElement::description)
                    .collect(Collectors.joining("; "));
            findings.add(new Finding(
                    ID,
                    Severity.WARNING,
                    "В политике не найдены некоторые обязательные сведения",
                    "Мы прочитали текст политики и не нашли в нём часть сведений, которые обычно должны там быть. "
                            + "Проверка автоматическая и ищет характерные формулировки — сведения могут быть "
                            + "изложены другими словами.",
                    policy.url(),
                    "Не найдено: " + missing + ". Найдено элементов: " + analysis.found().size()
                            + " из " + PolicyElement.values().length + ".",
                    "pd-policy-content",
                    "Рекомендуем проверить, что в политике указаны: " + missing + "."));
        }
        return findings;
    }
}
