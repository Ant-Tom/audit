package ru.siteaudit.siteaudit.web;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ru.siteaudit.siteaudit.audit.AuditService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Очередь проверок веб-интерфейса.
 *
 * <p>Проверки выполняются строго по одной в единственном рабочем потоке: объекты Playwright можно использовать
 * только из потока, который их создал (браузер создаётся лениво при первой проверке — как раз в этом потоке).
 * Результаты хранятся в памяти, не более {@code web.max-jobs} последних; при перезапуске пропадают.
 */
@Service
public class AuditJobs {

    private static final Logger log = LoggerFactory.getLogger(AuditJobs.class);

    private final AuditService auditService;
    private final int maxJobs;
    private final Clock clock;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audit-worker");
        t.setDaemon(true);
        return t;
    });
    /** В порядке создания; доступ только под synchronized (jobs). */
    private final Map<String, AuditJob> jobs = new LinkedHashMap<>();

    @Autowired
    public AuditJobs(AuditService auditService, @Value("${web.max-jobs:50}") int maxJobs) {
        this(auditService, maxJobs, Clock.systemUTC());
    }

    AuditJobs(AuditService auditService, int maxJobs, Clock clock) {
        this.auditService = auditService;
        this.maxJobs = Math.max(1, maxJobs);
        this.clock = clock;
    }

    /** Ставит проверку в очередь. Адрес должен быть уже нормализован ({@link ru.siteaudit.siteaudit.audit.TargetUrl}). */
    public AuditJob submit(String url) {
        AuditJob job = new AuditJob(UUID.randomUUID().toString(), url, clock.instant());
        synchronized (jobs) {
            jobs.put(job.id(), job);
            evictOldFinished();
        }
        worker.execute(() -> run(job));
        return job;
    }

    private void run(AuditJob job) {
        job.markRunning(clock.instant());
        try {
            AuditService.Audit audit = auditService.audit(job.url(), job::pageLoading);
            job.markDone(audit.result(), clock.instant());
        } catch (RuntimeException e) {
            log.error("Проверка {} завершилась с ошибкой", job.url(), e);
            job.markFailed(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), clock.instant());
        }
    }

    /** Удаляет самые старые завершённые проверки сверх лимита. Незавершённые не трогаем. */
    private void evictOldFinished() {
        int excess = jobs.size() - maxJobs;
        Iterator<AuditJob> it = jobs.values().iterator();
        while (excess > 0 && it.hasNext()) {
            if (it.next().status().finished()) {
                it.remove();
                excess--;
            }
        }
    }

    public Optional<AuditJob> find(String id) {
        synchronized (jobs) {
            return Optional.ofNullable(jobs.get(id));
        }
    }

    /** Все проверки, новые сверху. */
    public List<AuditJob> recent() {
        List<AuditJob> list;
        synchronized (jobs) {
            list = new ArrayList<>(jobs.values());
        }
        Collections.reverse(list);
        return list;
    }

    /** Сколько проверок стоит в очереди перед этой (0 — следующая или уже идёт). */
    public int queuePosition(AuditJob job) {
        synchronized (jobs) {
            int ahead = 0;
            for (AuditJob other : jobs.values()) {
                if (other == job) {
                    return ahead;
                }
                if (!other.status().finished()) {
                    ahead++;
                }
            }
            return 0;
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
