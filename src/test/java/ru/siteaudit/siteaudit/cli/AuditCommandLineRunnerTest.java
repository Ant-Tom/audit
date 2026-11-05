package ru.siteaudit.siteaudit.cli;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditCommandLineRunnerTest {

    @Test
    void printsUsageWithoutUrlAndDoesNotAudit() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        // AuditService = null: если раннер попробует запустить аудит, тест упадёт с NPE
        var runner = new AuditCommandLineRunner(null, List.of(), null, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        runner.run(new DefaultApplicationArguments("--out=./x"));

        assertThat(buffer.toString(StandardCharsets.UTF_8)).contains("Использование:");
        assertThat(runner.getExitCode()).isEqualTo(AuditCommandLineRunner.EXIT_USAGE);
    }

    @Test
    void normalizesUrl() {
        assertThat(AuditCommandLineRunner.normalizeUrl("example.ru")).isEqualTo("https://example.ru");
        assertThat(AuditCommandLineRunner.normalizeUrl("http://example.ru/a")).isEqualTo("http://example.ru/a");
        assertThat(AuditCommandLineRunner.normalizeUrl("ftp://example.ru")).isNull();
        assertThat(AuditCommandLineRunner.normalizeUrl("--out")).isNull();
        assertThat(AuditCommandLineRunner.normalizeUrl("http://localhost:8080/")).isEqualTo("http://localhost:8080/");
    }
}
