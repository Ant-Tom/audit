package ru.siteaudit.siteaudit.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.stereotype.Component;
import ru.siteaudit.siteaudit.model.AuditResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** report.json — полный {@link AuditResult}. */
@Component
public class JsonReportWriter implements ReportWriter {

    private final ObjectMapper mapper;

    public JsonReportWriter() {
        this.mapper = createMapper();
    }

    /** Общая настройка Jackson: даты в ISO-8601, отступы. */
    public static ObjectMapper createMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public String fileName() {
        return "report.json";
    }

    @Override
    public Path write(AuditResult result, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path file = outDir.resolve(fileName());
        mapper.writeValue(file.toFile(), result);
        return file;
    }

    public String toJson(Object value) throws IOException {
        return mapper.writeValueAsString(value);
    }
}
