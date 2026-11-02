package ru.siteaudit.siteaudit.parser;

import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.model.CheckboxInfo;
import ru.siteaudit.siteaudit.model.FieldInfo;
import ru.siteaudit.siteaudit.model.FormInfo;
import ru.siteaudit.siteaudit.model.LinkInfo;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.RawPage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.siteaudit.siteaudit.support.Fixtures.snapshot;

class PageParserTest {

    private final PageParser parser = new PageParser();

    @Test
    void goodSite_formWithConsentCheckbox() {
        PageSnapshot page = snapshot("good-site.html", "https://romashka.example/",
                "https://romashka.example/", "https://mc.yandex.ru/metrika/tag.js");

        assertThat(page.url()).isEqualTo("https://romashka.example/");
        assertThat(page.requestsBeforeInteraction()).contains("https://mc.yandex.ru/metrika/tag.js");
        assertThat(page.forms()).hasSize(1);

        FormInfo form = page.forms().getFirst();
        assertThat(form.action()).isEqualTo("https://romashka.example/api/callback");
        assertThat(form.method()).isEqualTo("POST");
        assertThat(form.fields()).extracting(FieldInfo::type)
                .containsExactly(FieldInfo.TYPE_NAME, FieldInfo.TYPE_PHONE, FieldInfo.TYPE_EMAIL);
        assertThat(form.fields().getFirst().label()).isEqualTo("Ваше имя");
        assertThat(form.collectsPersonalData()).isTrue();

        CheckboxInfo consent = form.checkboxes().getFirst();
        assertThat(consent.checked()).isFalse();
        assertThat(consent.labelText()).contains("согласие на обработку персональных данных");
        assertThat(consent.links()).containsExactly("https://romashka.example/soglasie-na-obrabotku-pd");
    }

    @Test
    void goodSite_legalAndExternalLinks_cookieBanner() {
        PageSnapshot page = snapshot("good-site.html", "https://romashka.example/");

        assertThat(page.legalLinks()).extracting(LinkInfo::href).contains(
                "https://romashka.example/soglasie-na-obrabotku-pd",
                "https://romashka.example/privacy-policy",
                "https://romashka.example/cookie-policy",
                "https://romashka.example/oferta");
        assertThat(page.legalLinks()).extracting(LinkInfo::href).doesNotContain("https://romashka.example/contacts");
        assertThat(page.externalLinks()).extracting(LinkInfo::href).containsExactly(
                "https://market.yandex.ru/store/romashka?erid=2VtzqvXyZ1", "https://vk.com/romashka");
        assertThat(page.hasCookieBanner()).isTrue();
        assertThat(page.visibleText()).contains("Доставка цветов по Москве");
    }

    @Test
    void noPolicy_formWithoutCheckbox_overHttp() {
        PageSnapshot page = snapshot("no-policy.html", "http://stroy.example/");

        assertThat(page.legalLinks()).isEmpty();
        assertThat(page.hasCookieBanner()).isFalse();
        FormInfo form = page.forms().getFirst();
        assertThat(form.action()).isEqualTo("http://stroy.example/order.php");
        assertThat(form.fields()).extracting(FieldInfo::type)
                .containsExactly(FieldInfo.TYPE_NAME, FieldInfo.TYPE_PHONE, "textarea");
        assertThat(form.checkboxes()).isEmpty();
    }

    @Test
    void precheckedCheckbox_labelFor_andAriaLabelHints() {
        PageSnapshot page = snapshot("prechecked-consent.html", "https://clinic.example/");

        FormInfo form = page.forms().getFirst();
        assertThat(form.fields()).extracting(FieldInfo::type)
                .containsExactly(FieldInfo.TYPE_NAME, FieldInfo.TYPE_PHONE);
        CheckboxInfo checkbox = form.checkboxes().getFirst();
        assertThat(checkbox.checked()).isTrue();
        assertThat(checkbox.name()).isEqualTo("agree");
        assertThat(checkbox.labelText()).isEqualTo("Согласен с политикой конфиденциальности");
        assertThat(checkbox.links()).containsExactly("https://clinic.example/politika-konfidencialnosti");
    }

    @Test
    void formWithoutFormTag_detectedByContainerWithSubmitButton() {
        PageSnapshot page = snapshot("formless-form-bitrix.html", "https://shop.example/");

        List<FormInfo> pdForms = page.forms().stream().filter(FormInfo::collectsPersonalData).toList();
        assertThat(pdForms).hasSize(1);
        FormInfo form = pdForms.getFirst();
        assertThat(form.action()).isEmpty();
        assertThat(form.method()).isEmpty();
        assertThat(form.fields()).extracting(FieldInfo::name).containsExactly("NAME", "PHONE");
        assertThat(form.checkboxes()).singleElement().satisfies(cb -> {
            assertThat(cb.labelText()).contains("оферты");
            assertThat(cb.links()).containsExactly("https://shop.example/oferta/");
        });
    }

    @Test
    void cookieTextInFooterIsNotABanner() {
        PageSnapshot page = snapshot("metrika-no-banner.html", "https://store.example/");

        assertThat(page.hasCookieBanner()).isFalse();
        assertThat(page.forms()).isEmpty();
        assertThat(page.legalLinks()).extracting(LinkInfo::href).containsExactly("https://store.example/privacy");
    }

    @Test
    void floatingBlockMarkedByCrawlerIsABanner() {
        assertThat(snapshot("floating-banner.html", "https://x.example/").hasCookieBanner()).isTrue();
    }

    @Test
    void inlineFixedStyleIsABanner() {
        String html = "<html><body><div style='position: fixed; bottom: 0'>"
                + "<span>Мы используем файлы cookie</span></div></body></html>";
        PageSnapshot page = parser.parse(new RawPage("https://x.example/", html, List.of()));
        assertThat(page.hasCookieBanner()).isTrue();
    }

    @Test
    void visibleTextExcludesScriptsAndHiddenElements() {
        String html = "<html><body><p>Видно</p><script>var secret=1</script><div hidden>Скрыто</div></body></html>";
        PageSnapshot page = parser.parse(new RawPage("https://x.example/", html, List.of()));
        assertThat(page.visibleText()).isEqualTo("Видно");
    }

    @Test
    void checkboxWithoutLabel_textStopsBeforeSubmitButton() {
        String html = "<html><body><div class='cb'><input name='n' placeholder='Имя'>"
                + "<input type='checkbox' name='a' checked><span>Согласен с <a href='/policy'>политикой</a></span>"
                + "<div class='btn'>Перезвоните мне</div></div></body></html>";
        PageSnapshot page = parser.parse(new RawPage("https://x.example/", html, List.of()));
        CheckboxInfo cb = page.forms().getFirst().checkboxes().getFirst();
        assertThat(cb.labelText()).isEqualTo("Согласен с политикой");
        assertThat(cb.links()).containsExactly("https://x.example/policy");
        assertThat(cb.checked()).isTrue();
    }
}
