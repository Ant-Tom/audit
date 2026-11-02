package ru.siteaudit.siteaudit.legal;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/** Нормы права и штрафы из legal.yml. Ключ — {@code Finding.legalRef}. */
@ConfigurationProperties("legal")
public record LegalProperties(Map<String, Norm> norms) {

    public LegalProperties {
        norms = norms == null ? Map.of() : Map.copyOf(norms);
    }

    public Optional<Norm> find(String legalRef) {
        return Optional.ofNullable(legalRef).map(norms::get);
    }

    /**
     * @param title           о чём требование
     * @param article         статья закона
     * @param summary         суть требования простыми словами
     * @param liability       статья об ответственности
     * @param fineLegalEntity штраф для юрлиц (текстом), может отсутствовать
     * @param verifiedAt      дата ручной проверки; {@code null} — сведения не проверены
     */
    public record Norm(
            String title,
            String article,
            String summary,
            String liability,
            String fineLegalEntity,
            LocalDate verifiedAt
    ) {
        public boolean verified() {
            return verifiedAt != null;
        }
    }
}
