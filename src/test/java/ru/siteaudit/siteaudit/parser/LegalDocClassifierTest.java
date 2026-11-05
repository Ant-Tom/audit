package ru.siteaudit.siteaudit.parser;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.parser.LegalDocClassifier.DocKind;

import static org.assertj.core.api.Assertions.assertThat;

class LegalDocClassifierTest {

    @Test
    void classifiesTypicalLinks() {
        assertThat(LegalDocClassifier.classify("Политика обработки персональных данных", "/policy"))
                .containsExactly(DocKind.POLICY);
        assertThat(LegalDocClassifier.classify("Политика конфиденциальности", "/privacy"))
                .containsExactly(DocKind.POLICY);
        assertThat(LegalDocClassifier.classify("согласие на обработку персональных данных", "/soglasie"))
                .containsExactly(DocKind.CONSENT);
        assertThat(LegalDocClassifier.classify("Политика использования cookie", "/cookie-policy"))
                .containsExactly(DocKind.COOKIE);
        assertThat(LegalDocClassifier.classify("Публичная оферта", "/oferta")).containsExactly(DocKind.OFFER);
    }

    @Test
    void usesHrefWhenTextIsGeneric() {
        assertThat(LegalDocClassifier.classify("здесь", "https://a.example/politika-konfidencialnosti"))
                .containsExactly(DocKind.POLICY);
        assertThat(LegalDocClassifier.classify("здесь", "https://a.example/soglasie-na-obrabotku"))
                .containsExactly(DocKind.CONSENT);
        assertThat(LegalDocClassifier.classify("Документ",
                "https://a.example/%D0%BF%D0%BE%D0%BB%D0%B8%D1%82%D0%B8%D0%BA%D0%B0"))
                .containsExactly(DocKind.POLICY);
    }

    @Test
    void ordinaryLinksAreNotLegal() {
        assertThat(LegalDocClassifier.isLegal("Контакты", "/contacts")).isFalse();
        assertThat(LegalDocClassifier.isLegal("Каталог", "/catalog/special-offers")).isFalse();
    }
}
