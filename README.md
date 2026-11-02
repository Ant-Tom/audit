# site-audit — автоматическая проверка сайта на признаки нарушений 152-ФЗ

Приложение принимает адрес сайта, открывает его в headless-браузере как обычный посетитель
(ни на что не нажимая) и формирует отчёт о **признаках** возможных нарушений требований
к обработке персональных данных, cookie и базовых требований к сайтам.

> Отчёт не является юридическим заключением. Программа ищет характерные признаки и может как пропустить
> проблему, так и указать на то, что проблемой не является. Формулировки отчёта — «обнаружены признаки»,
> «рекомендуем проверить».

Пример отчёта по демо-сайту: [`docs/example/report.html`](docs/example/report.html)
(исходники демо-сайта — в [`docs/example/demo-site`](docs/example/demo-site)).

## Что проверяется

| Код | Уровень | Что ищем |
|---|---|---|
| `pd-policy-link` | CRITICAL | Нет ссылки на политику обработки ПДн («политик», «персональн», «конфиденц») |
| `pd-policy-content` | WARNING | В тексте политики не найдены: оператор, ИНН/ОГРН, цели, перечень данных, сроки, права субъекта, отзыв согласия |
| `pd-form-consent` | CRITICAL | Форма собирает имя / телефон / email, но чекбокса согласия нет |
| `pd-consent-prechecked` | CRITICAL | Чекбокс согласия отмечен заранее |
| `pd-consent-separate` | WARNING | Чекбокс согласия ссылается на политику / оферту, а не на отдельный документ согласия |
| `cookie-banner` | WARNING | Нет уведомления об использовании cookie |
| `trackers-before-consent` | CRITICAL | Метрика, VK Pixel, Top@Mail.ru, GA, Facebook Pixel и т.п. загружаются до согласия |
| `foreign-services` | WARNING | Запросы к иностранным сервисам (GA, Google Fonts, reCAPTCHA…) — признак трансграничной передачи |
| `https` | CRITICAL | Сайт открывается по HTTP или форма отправляет данные по HTTP |
| `ad-erid` | INFO | Внешние ссылки, похожие на рекламные / партнёрские, без `erid` |

## Требования

- Java 21, Maven 3.9+
- Chromium для Playwright. При первом запуске Playwright скачивает его сам (~150 МБ в `~/.cache/ms-playwright`).
  Если браузер уже установлен — укажите `PLAYWRIGHT_BROWSERS_PATH` или `crawler.browser.executable-path`.

## Сборка и запуск

```bash
mvn package
```

```bash
java -jar target/site-audit.jar https://example.ru --out=./reports
```

или без сборки jar:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="https://example.ru --out=./reports"
```

Параметры:

| Параметр | Описание |
|---|---|
| `<URL>` | Адрес сайта. Без схемы добавляется `https://` |
| `--out=<путь>` | Каталог для `report.json` и `report.html` (по умолчанию `./reports`) |
| `--snapshots` | Дополнительно сохранить `snapshots.json` — разобранные страницы, удобно при отладке правил |
| `--crawler.*=…` | Любая настройка из `application.yml`, например `--crawler.max-pages=5` |

Без URL приложение печатает справку и не запускает проверку.

Коды выхода: `0` — проверка выполнена (независимо от числа находок), `1` — не удалось загрузить ни одной
страницы или сохранить отчёт, `2` — неверные аргументы.

Если в консоли вместо кириллицы знаки вопроса — терминал не в UTF-8; добавьте `-Dstdout.encoding=UTF-8`.

Jar весит ~220 МБ: Playwright for Java включает драйвер для всех платформ. Это ожидаемо.

## Настройки

| Файл | Что внутри |
|---|---|
| `src/main/resources/application.yml` | Обход: лимиты страниц, таймауты, задержка, User-Agent, robots.txt, sitemap, прокси браузера |
| `src/main/resources/legal.yml` | Нормы права и штрафы. Ключ записи = `Finding.legalRef`. **В коде правил статей и сумм нет** |
| `src/main/resources/trackers.yml` | Домены трекеров (`tracker: true`), иностранных сервисов (`foreign: true`), признаки рекламных ссылок |

Все записи `legal.yml` пока помечены `verifiedAt: null  # TODO: verify` — сведения заполнены по памяти
и требуют проверки юристом. Пока дата не проставлена, отчёт показывает рядом с нормой пометку
«Сведения о норме и штрафе требуют уточнения». После проверки укажите дату: `verifiedAt: 2026-11-15`.

Основные настройки обхода:

```yaml
crawler:
  max-pages: 15              # внутренних страниц помимо главной
  max-legal-pages: 5         # страниц политики / согласия / cookie / оферты сверх max-pages
  navigation-timeout: 30s
  network-idle-timeout: 10s  # сколько ждать «тишины в сети» — счётчики грузятся с задержкой
  delay-between-requests: 1s # robots.txt Crawl-delay учитывается (не более 10 с)
  retries: 1
  respect-robots-txt: true
  browser:
    executable-path: ""      # свой Chromium
    proxy-server: ""         # например http://127.0.0.1:3128
```

## Архитектура

```
URL → SiteCrawler (Playwright) → RawPage → PageParser (Jsoup) → PageSnapshot → Rule × N → Finding → AuditResult → ReportWriter × N
```

Playwright только рендерит страницы и собирает сетевые запросы. Весь разбор DOM — в `PageParser`
по отрендеренному HTML, поэтому парсер и правила тестируются на HTML-фикстурах без браузера.

```
ru.siteaudit.siteaudit
  crawler/  SiteCrawler, BrowserConfig (Playwright и Browser — ленивые бины), RobotsTxt, SitemapParser,
            LinkPrioritizer, UrlUtils, CrawlerProperties
  parser/   PageParser, FieldTypeDetector, CookieBannerDetector, LegalDocClassifier
  model/    RawPage, PageSnapshot, FormInfo, FieldInfo, CheckboxInfo, LinkInfo, Finding, Severity,
            AuditResult, CrawlWarning
  rules/    Rule + по классу на правило; FormSupport, Evidence; policy/ — PolicyAnalyzer
  legal/    LegalProperties (legal.yml), TrackerProperties (trackers.yml)
  report/   ReportWriter, JsonReportWriter, HtmlReportWriter, ReportView
  audit/    AuditService — единая точка входа (CLI сейчас, REST и планировщик потом)
  cli/      AuditCommandLineRunner
```

Как устроен обход:

- Один `Playwright` и один `Browser` на приложение; на каждый аудит — новый `BrowserContext`,
  чтобы cookie разных проверок не смешивались.
- `page.onRequest` подключается до `page.navigate`, по странице никто не кликает — значит, все
  собранные запросы сделаны **до согласия** посетителя. После загрузки ждём `NETWORKIDLE` с таймаутом.
- Перед снятием HTML краулер переносит в атрибуты то, что не видно в разметке: состояние чекбоксов,
  выставленное скриптом (`checked`), и вычисленное `position: fixed/sticky` (`data-sa-position`) —
  признак плавающего баннера.
- Порядок: главная → страницы юридических документов (отдельный лимит) → ссылки с «контакты», «заказ»,
  «корзина», «обратн» и т.п. → остальные. Адреса берутся со страниц и из `sitemap.xml`.
- Ошибка одной страницы не прерывает аудит, она попадает в `AuditResult.warnings`.

## Как добавить правило

1. Создайте класс в `rules/`, реализующий `Rule`, и пометьте его `@Component`:

   ```java
   @Component
   public class PhoneMaskRule implements Rule {

       public static final String ID = "phone-mask";

       @Override
       public String id() {
           return ID;
       }

       @Override
       public List<Finding> check(List<PageSnapshot> pages) {
           List<Finding> findings = new ArrayList<>();
           for (FormSupport.FormOccurrences occ : FormSupport.distinctForms(pages)) {
               // ... условие ...
               findings.add(new Finding(ID, Severity.WARNING,
                       "Короткий заголовок для владельца бизнеса",
                       "Что обнаружено и почему это может быть важно — без утверждений «вы нарушили».",
                       occ.firstPage(),
                       Evidence.describeForm(occ.form()),
                       "some-norm",                        // ключ из legal.yml
                       "Рекомендуем проверить ..."));
           }
           return findings;
       }
   }
   ```

2. Если правило ссылается на норму, которой ещё нет, добавьте запись в `legal.yml`
   с `verifiedAt: null  # TODO: verify`. Статьи и суммы — **только** там.
3. Напишите юнит-тест на `PageSnapshot` (см. `src/test/java/.../rules/*Test.java`, хелперы `Pages` и `Fixtures`).
   Если правилу нужны новые данные со страницы — добавьте поле в `PageSnapshot`, заполните его
   в `PageParser` и проверьте на HTML-фикстуре.
4. Добавьте id правила в `ApplicationContextTest`.

Больше ничего менять не нужно: `AuditService` получает все бины `Rule` списком, отчёт выводит любые находки.

Правила о тексте документов используют интерфейсы, чтобы заменить эвристику без изменения правила:
например, `PolicyAnalyzer` (сейчас `RegexPolicyAnalyzer`) можно заменить на LLM-реализацию, объявив её бином.

Принципы текстов находок:

- только «обнаружены признаки», «рекомендуем проверить»; никогда — «сайт соответствует закону» или «вы нарушили закон»;
- язык владельца бизнеса, а не программиста;
- одна находка на сайт, а не на каждую страницу (одинаковые формы и трекеры группируются).

## Тесты

```bash
mvn test                 # юнит-тесты: парсер, правила, отчёты, CLI — без браузера
```

```bash
mvn test -Pintegration   # плюс интеграционный тест краулера (@Tag("integration")) — нужен Chromium
```

Интеграционный тест поднимает локальный сайт на `com.sun.net.httpserver.HttpServer` и проверяет:
robots.txt, sitemap, приоритеты обхода, отложенные запросы «счётчика», чекбокс, отмеченный скриптом,
cookie-баннер с `position: fixed` из CSS, обработку 404.

HTML-фикстуры — `src/test/resources/fixtures`.

## Ограничения и эвристики

- **Формы без тега `<form>`** — поиск ближайшего контейнера с кнопкой отправки (`TODO` в `PageParser`).
  Две формы в одном контейнере сольются в одну; кнопка без характерного класса или текста может быть не найдена.
- **Тип поля** (имя / телефон / email) — по `type`, `name`, `id`, `placeholder`, `label`, `aria-label`, `autocomplete`.
- **Cookie-баннер** — текст «cookie / куки» в плавающем блоке, диалоге или блоке с «баннерным» классом.
  Баннеры внутри iframe и закрытого shadow DOM не видны.
- **Политика в PDF** или на другом домене не анализируется — выдаётся INFO «текст политики не проверен».
- **Тип документа по ссылке** определяется по тексту ссылки и адресу (`LegalDocClassifier`).
- **Реклама** — только INFO по списку признаков из `trackers.yml`: по ссылке нельзя понять, реклама ли это.
- Проверяется не весь сайт, а главная и до `max-pages` + `max-legal-pages` страниц.

## Дальше: SaaS

- **REST API**: добавить `spring-boot-starter-web`, убрать `web-application-type: none`, контроллер вызывает
  `AuditService.audit(url)`. CLI-раннер без URL ничего не делает, а `main` не завершает процесс в веб-режиме.
- **Очередь проверок**: объекты Playwright не потокобезопасны, поэтому сейчас `SiteCrawler.crawl` синхронизирован.
  Для параллельных аудитов нужен пул «Playwright + Browser на рабочий поток» — изменения только в `crawler/`.
- **Мониторинг по расписанию**: `@Scheduled` поверх `AuditService` и хранение `AuditResult`
  (он сериализуется в JSON как есть) для сравнения с предыдущей проверкой.
