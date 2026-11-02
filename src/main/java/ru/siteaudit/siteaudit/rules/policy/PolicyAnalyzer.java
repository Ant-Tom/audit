package ru.siteaudit.siteaudit.rules.policy;

/**
 * Анализ текста политики обработки персональных данных. Сейчас — регулярные выражения
 * ({@link RegexPolicyAnalyzer}); интерфейс позволяет подключить LLM-реализацию, не меняя правило.
 */
public interface PolicyAnalyzer {

    PolicyAnalysis analyze(String policyText);
}
