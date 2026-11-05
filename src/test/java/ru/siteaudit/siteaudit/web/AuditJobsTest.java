package ru.siteaudit.siteaudit.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.siteaudit.siteaudit.audit.AuditService;
import ru.siteaudit.siteaudit.model.AuditResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AuditJobsTest {

    private AuditJobs jobs;

    @AfterEach
    void stop() {
        if (jobs != null) {
            jobs.shutdown();
        }
    }

    /** AuditService без браузера: «загружает» одну страницу и возвращает пустой результат, либо падает. */
    private static AuditService fakeService(CountDownLatch gate, boolean fail) {
        return new AuditService(null, null, List.of()) {
            @Override
            public Audit audit(String url, Consumer<String> onPageLoad) {
                try {
                    gate.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                onPageLoad.accept(url);
                if (fail) {
                    throw new IllegalStateException("браузер не запустился");
                }
                return new Audit(new AuditResult(url, Instant.EPOCH, Instant.EPOCH, List.of(url), List.of(), List.of()),
                        List.of());
            }
        };
    }

    @Test
    void runsJobsOneByOneAndReportsQueue() {
        CountDownLatch gate = new CountDownLatch(1);
        jobs = new AuditJobs(fakeService(gate, false), 10, Clock.systemUTC());

        AuditJob first = jobs.submit("https://a.example");
        AuditJob second = jobs.submit("https://b.example");

        await().atMost(Duration.ofSeconds(5)).until(() -> first.status() == AuditJob.Status.RUNNING);
        assertThat(second.status()).isEqualTo(AuditJob.Status.QUEUED);
        assertThat(jobs.queuePosition(second)).isEqualTo(1);
        assertThat(jobs.recent()).containsExactly(second, first);

        gate.countDown();
        await().atMost(Duration.ofSeconds(5)).until(() -> second.status() == AuditJob.Status.DONE);
        assertThat(first.result().targetUrl()).isEqualTo("https://a.example");
        assertThat(first.pagesLoaded()).isEqualTo(1);
        assertThat(first.currentPage()).isNull();
    }

    @Test
    void failureIsRecordedAndNextJobStillRuns() {
        CountDownLatch open = new CountDownLatch(0);
        jobs = new AuditJobs(fakeService(open, true), 10, Clock.systemUTC());

        AuditJob job = jobs.submit("https://a.example");

        await().atMost(Duration.ofSeconds(5)).until(() -> job.status() == AuditJob.Status.FAILED);
        assertThat(job.error()).contains("браузер не запустился");
        assertThat(job.result()).isNull();
    }

    @Test
    void keepsOnlyLatestFinishedJobs() {
        jobs = new AuditJobs(fakeService(new CountDownLatch(0), false), 2, Clock.systemUTC());

        AuditJob oldest = jobs.submit("https://a.example");
        await().atMost(Duration.ofSeconds(5)).until(() -> oldest.status().finished());
        AuditJob middle = jobs.submit("https://b.example");
        await().atMost(Duration.ofSeconds(5)).until(() -> middle.status().finished());
        AuditJob newest = jobs.submit("https://c.example");

        assertThat(jobs.find(oldest.id())).isEmpty();
        assertThat(jobs.recent()).containsExactly(newest, middle);
    }
}
