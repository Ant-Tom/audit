package ru.siteaudit.siteaudit.rules;

import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;
import ru.siteaudit.siteaudit.model.Severity;

import java.util.List;

/** Ни на одной странице не найден блок с уведомлением об использовании cookie. */
@Component
public class CookieBannerRule implements Rule {

    public static final String ID = "cookie-banner";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Finding> check(List<PageSnapshot> pages) {
        if (pages.isEmpty() || pages.stream().anyMatch(PageSnapshot::hasCookieBanner)) {
            return List.of();
        }
        return List.of(new Finding(
                ID,
                Severity.WARNING,
                "Не найдено уведомление об использовании cookie",
                "Мы не нашли на сайте всплывающего уведомления или баннера о cookie. Если сайт использует cookie "
                        + "для аналитики или рекламы (например, счётчики посещаемости), посетителя рекомендуется "
                        + "предупредить об этом и получить его согласие.",
                pages.getFirst().url(),
                "Проверено страниц: " + pages.size() + ". Плавающих блоков с упоминанием cookie не найдено.",
                "pd-cookies",
                "Рекомендуем проверить, какие cookie ставит сайт и сторонние сервисы. Если они используются для "
                        + "аналитики или рекламы — добавьте баннер с возможностью согласиться или отказаться "
                        + "и ссылкой на политику использования cookie. Если баннер есть, но мы его не нашли "
                        + "(например, он внутри iframe), проверьте это вручную."));
    }
}
