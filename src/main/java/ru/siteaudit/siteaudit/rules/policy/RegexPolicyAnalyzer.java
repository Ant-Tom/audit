package ru.siteaudit.siteaudit.rules.policy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Поиск обязательных элементов политики регулярными выражениями. Даёт ложные срабатывания в обе стороны:
 * находит слова, но не проверяет смысл. Отчёт поэтому говорит «не найдено», а не «отсутствует».
 */
@Component
public class RegexPolicyAnalyzer implements PolicyAnalyzer {

    private static final int FRAGMENT_RADIUS = 60;

    private static final Map<PolicyElement, Pattern> PATTERNS = new EnumMap<>(Map.of(
            PolicyElement.OPERATOR_NAME, Pattern.compile(
                    "(?U)(?<![\\w])(ооо|ао|пао|зао|оао|нко|ано|ип)\\s*[«\"„“]?\\s*[\\w«]"
                            + "|общество с ограниченной ответственностью|акционерное общество"
                            + "|индивидуальный предприниматель"),
            PolicyElement.OPERATOR_ID, Pattern.compile(
                    "(?U)(?<![\\w])(инн|огрн|огрнип)\\W{0,15}\\d{10,15}"),
            PolicyElement.PURPOSES, Pattern.compile(
                    "(?U)цел[ьиеяй]\\w*\\s+(обработки|сбора|использования)|в\\s+целях|для\\s+целей"),
            PolicyElement.DATA_CATEGORIES, Pattern.compile(
                    "(?U)(перечень|состав|категори\\w*|объ[её]м)\\s+(\\w+\\s+){0,2}персональных\\s+данных"
                            + "|обрабатываются\\s+следующие|обрабатывает\\s+следующие"
                            + "|(фамилия|ф\\.?\\s?и\\.?\\s?о\\.?).{0,80}(телефон|e-?mail|электронн)"),
            PolicyElement.RETENTION, Pattern.compile(
                    "(?U)срок\\w*\\s+(\\w+\\s+){0,2}(обработки|хранения)|хран\\w+\\s.{0,60}(в течение|до достижения|не более)"),
            PolicyElement.SUBJECT_RIGHTS, Pattern.compile(
                    "(?U)прав\\w*\\s+(субъект|пользовател|посетител)\\w*|(субъект|пользовател)\\w*.{0,60}(имеет право|вправе)"),
            PolicyElement.CONSENT_WITHDRAWAL, Pattern.compile(
                    "(?U)отзыв\\w*\\s+(\\w+\\s+){0,2}согласи|отозвать\\s+(\\w+\\s+){0,2}согласи")
    ));

    @Override
    public PolicyAnalysis analyze(String policyText) {
        String text = policyText == null ? "" : policyText.toLowerCase(Locale.ROOT).replace('ё', 'е');
        Map<PolicyElement, String> found = new EnumMap<>(PolicyElement.class);
        List<PolicyElement> missing = new ArrayList<>();
        for (PolicyElement element : PolicyElement.values()) {
            Matcher m = PATTERNS.get(element).matcher(text);
            if (m.find()) {
                found.put(element, fragment(policyText, m.start(), m.end()));
            } else {
                missing.add(element);
            }
        }
        return new PolicyAnalysis(found, missing);
    }

    private static String fragment(String original, int start, int end) {
        int from = Math.max(0, start - FRAGMENT_RADIUS);
        int to = Math.min(original.length(), end + FRAGMENT_RADIUS);
        return (from > 0 ? "…" : "") + original.substring(from, to).trim() + (to < original.length() ? "…" : "");
    }
}
