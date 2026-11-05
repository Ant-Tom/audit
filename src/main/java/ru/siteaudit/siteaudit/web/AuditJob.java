package ru.siteaudit.siteaudit.web;

import ru.siteaudit.siteaudit.model.AuditResult;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Одна проверка, запущенная из веб-интерфейса. Поля меняет рабочий поток аудита, читают — потоки запросов,
 * поэтому всё изменяемое состояние volatile / atomic.
 */
public final class AuditJob {

    public enum Status {
        QUEUED("В очереди"),
        RUNNING("Идёт проверка"),
        DONE("Готово"),
        FAILED("Ошибка");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public boolean finished() {
            return this == DONE || this == FAILED;
        }
    }

    private final String id;
    private final String url;
    private final Instant createdAt;
    private final AtomicInteger pagesLoaded = new AtomicInteger();
    private volatile Status status = Status.QUEUED;
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private volatile String currentPage;
    private volatile AuditResult result;
    private volatile String error;

    AuditJob(String id, String url, Instant createdAt) {
        this.id = id;
        this.url = url;
        this.createdAt = createdAt;
    }

    void markRunning(Instant now) {
        startedAt = now;
        status = Status.RUNNING;
    }

    void pageLoading(String pageUrl) {
        currentPage = pageUrl;
        pagesLoaded.incrementAndGet();
    }

    void markDone(AuditResult auditResult, Instant now) {
        result = auditResult;
        finishedAt = now;
        currentPage = null;
        status = Status.DONE;
    }

    void markFailed(String message, Instant now) {
        error = message;
        finishedAt = now;
        currentPage = null;
        status = Status.FAILED;
    }

    public String id() {
        return id;
    }

    public String url() {
        return url;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Status status() {
        return status;
    }

    /** Сколько страниц робот начал загружать (с повторами). Для показа хода проверки, не для отчёта. */
    public int pagesLoaded() {
        return pagesLoaded.get();
    }

    public String currentPage() {
        return currentPage;
    }

    public AuditResult result() {
        return result;
    }

    public String error() {
        return error;
    }

    /** Время работы: от старта до конца или до {@code now}, если ещё идёт; ноль, пока в очереди. */
    public Duration elapsed(Instant now) {
        Instant start = startedAt;
        if (start == null) {
            return Duration.ZERO;
        }
        Instant end = finishedAt;
        return Duration.between(start, end == null ? now : end);
    }
}
