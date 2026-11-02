package ru.siteaudit.siteaudit.rules.policy;

import java.util.List;
import java.util.Map;

/**
 * Результат анализа текста политики.
 *
 * @param found   найденные элементы и фрагмент текста, по которому они найдены
 * @param missing элементы, которые не удалось найти
 */
public record PolicyAnalysis(Map<PolicyElement, String> found, List<PolicyElement> missing) {
    public PolicyAnalysis {
        found = Map.copyOf(found);
        missing = List.copyOf(missing);
    }
}
