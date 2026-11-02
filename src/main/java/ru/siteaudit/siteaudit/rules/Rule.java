package ru.siteaudit.siteaudit.rules;

import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.PageSnapshot;

import java.util.List;

/**
 * Правило проверки. Каждое правило — Spring-бин ({@code @Component}); AuditService получает их все
 * списком. Чтобы добавить проверку, достаточно создать новый класс, реализующий этот интерфейс.
 *
 * <p>Правило получает все страницы сайта сразу, чтобы делать выводы на уровне сайта
 * («ни на одной странице нет ссылки на политику») и не дублировать одинаковые находки со страниц.
 *
 * <p>Тексты находок: только «обнаружены признаки», «рекомендуем проверить». Статьи и штрафы в коде
 * не указываются — только ключ {@code legalRef} из legal.yml.
 */
public interface Rule {

    /** Стабильный идентификатор, например {@code pd-policy-link}. */
    String id();

    /** @param pages страницы сайта; первая — главная */
    List<Finding> check(List<PageSnapshot> pages);
}
