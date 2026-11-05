package ru.siteaudit.siteaudit.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import ru.siteaudit.siteaudit.audit.TargetUrl;
import ru.siteaudit.siteaudit.crawler.CrawlerProperties;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.Severity;
import ru.siteaudit.siteaudit.report.HtmlReportWriter;
import ru.siteaudit.siteaudit.report.JsonReportWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Веб-интерфейс: форма проверки, ход проверки, отчёт. Работает только в режиме {@code --web}. */
@Controller
public class WebController {

    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("d MMM, HH:mm", Locale.forLanguageTag("ru"))
            .withZone(ZoneId.of("Europe/Moscow"));

    private final AuditJobs jobs;
    private final HtmlReportWriter html;
    private final JsonReportWriter json;
    private final CrawlerProperties crawler;
    private final Clock clock;

    public WebController(AuditJobs jobs, HtmlReportWriter html, JsonReportWriter json, CrawlerProperties crawler) {
        this.jobs = jobs;
        this.html = html;
        this.json = json;
        this.crawler = crawler;
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/")
    public ModelAndView index() {
        return indexView("", null, HttpStatus.OK);
    }

    @PostMapping("/audits")
    public ModelAndView submit(@RequestParam(name = "url", defaultValue = "") String rawUrl) {
        Optional<String> url = TargetUrl.normalize(rawUrl);
        if (url.isEmpty()) {
            return indexView(rawUrl, "Не похоже на адрес сайта. Пример: example.ru или https://example.ru",
                    HttpStatus.BAD_REQUEST);
        }
        AuditJob job = jobs.submit(url.get());
        return new ModelAndView("redirect:/audits/" + job.id());
    }

    @GetMapping("/audits/{id}")
    public ModelAndView job(@PathVariable String id) {
        AuditJob job = require(id);
        if (job.status() == AuditJob.Status.DONE) {
            return new ModelAndView("redirect:/audits/" + id + "/report");
        }
        ModelAndView view = new ModelAndView("job");
        view.addObject("job", job);
        view.addObject("status", status(job));
        return view;
    }

    /** Состояние проверки для опроса со страницы хода проверки. */
    @GetMapping(path = "/audits/{id}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> status(@PathVariable String id) {
        return status(require(id));
    }

    @GetMapping(path = "/audits/{id}/report", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> report(@PathVariable String id) {
        AuditJob job = require(id);
        if (job.status() != AuditJob.Status.DONE) {
            return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create("/audits/" + id)).build();
        }
        String page = html.render(job.result(), "/", "/audits/" + id + "/report.json");
        return ResponseEntity.ok().contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(page);
    }

    @GetMapping(path = "/audits/{id}/report.json")
    public ResponseEntity<String> reportJson(@PathVariable String id) {
        AuditJob job = require(id);
        if (job.status() != AuditJob.Status.DONE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Проверка ещё не завершена");
        }
        try {
            return ResponseEntity.ok()
                    .contentType(new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename("report.json").build().toString())
                    .body(json.toJson(job.result()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ModelAndView indexView(String url, String error, HttpStatus status) {
        ModelAndView view = new ModelAndView("index");
        view.addObject("url", url);
        view.addObject("error", error);
        view.addObject("maxPages", crawler.maxPages());
        view.addObject("jobs", jobs.recent().stream().map(this::row).toList());
        view.setStatus(status);
        return view;
    }

    private AuditJob require(String id) {
        return jobs.find(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Проверка не найдена — возможно, приложение перезапускали"));
    }

    private Map<String, Object> status(AuditJob job) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("id", job.id());
        s.put("url", job.url());
        s.put("status", job.status().name());
        s.put("statusLabel", job.status().label());
        s.put("queuePosition", job.status() == AuditJob.Status.QUEUED ? jobs.queuePosition(job) : 0);
        s.put("pagesLoaded", job.pagesLoaded());
        s.put("currentPage", job.currentPage());
        s.put("elapsedSeconds", job.elapsed(clock.instant()).toSeconds());
        s.put("error", job.error());
        s.put("reportUrl", job.status() == AuditJob.Status.DONE ? "/audits/" + job.id() + "/report" : null);
        return s;
    }

    private JobRow row(AuditJob job) {
        AuditResult result = job.result();
        Map<Severity, Long> counts = result == null ? null : result.countsBySeverity();
        return new JobRow(job.id(), job.url(), job.status().label(), job.status().name().toLowerCase(Locale.ROOT),
                TIME.format(job.createdAt()),
                counts == null ? null : counts.get(Severity.CRITICAL),
                counts == null ? null : counts.get(Severity.WARNING),
                counts == null ? null : counts.get(Severity.INFO),
                result == null ? null : result.pagesScanned().size());
    }

    /** Строка списка проверок на главной. Счётчики — только у завершённых. */
    public record JobRow(String id, String url, String statusLabel, String statusCss, String createdAt,
                         Long critical, Long warning, Long info, Integer pages) {
        public boolean done() {
            return critical != null;
        }
    }
}
