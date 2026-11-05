package ru.siteaudit.siteaudit.parser;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.FieldInfo;

import static org.assertj.core.api.Assertions.assertThat;

class FieldTypeDetectorTest {

    @Test
    void detectsByHtmlType() {
        assertThat(FieldTypeDetector.detect("email")).isEqualTo(FieldInfo.TYPE_EMAIL);
        assertThat(FieldTypeDetector.detect("tel")).isEqualTo(FieldInfo.TYPE_PHONE);
    }

    @Test
    void detectsByHints() {
        assertThat(FieldTypeDetector.detect("text", "your-name")).isEqualTo(FieldInfo.TYPE_NAME);
        assertThat(FieldTypeDetector.detect("text", "form_text_1", "", "Ваше имя")).isEqualTo(FieldInfo.TYPE_NAME);
        assertThat(FieldTypeDetector.detect("text", "UF_FIO")).isEqualTo(FieldInfo.TYPE_NAME);
        assertThat(FieldTypeDetector.detect("text", "f1", "", "Телефон")).isEqualTo(FieldInfo.TYPE_PHONE);
        assertThat(FieldTypeDetector.detect("text", "user_tel")).isEqualTo(FieldInfo.TYPE_PHONE);
        assertThat(FieldTypeDetector.detect("text", "mail", "", "Электронная почта")).isEqualTo(FieldInfo.TYPE_EMAIL);
    }

    @Test
    void doesNotConfuseSimilarWords() {
        assertThat(FieldTypeDetector.detect("text", "username")).isEqualTo("text");
        assertThat(FieldTypeDetector.detect("text", "company_name", "", "Название компании")).isEqualTo("text");
        assertThat(FieldTypeDetector.detect("text", "hotel")).isEqualTo("text");
        assertThat(FieldTypeDetector.detect("textarea", "message", "", "Сообщение")).isEqualTo("textarea");
        assertThat(FieldTypeDetector.detect("number", "phone")).isEqualTo("number");
    }
}
