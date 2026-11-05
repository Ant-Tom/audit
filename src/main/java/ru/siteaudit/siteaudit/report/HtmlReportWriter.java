package ru.siteaudit.siteaudit.report;

import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import ru.siteaudit.siteaudit.legal.LegalProperties;
import ru.siteaudit.siteaudit.model.AuditResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * report.html — один самодостаточный файл (CSS внутри), понятный владельцу бизнеса.
 * Свой SpringTemplateEngine (выражения на SpEL), чтобы не зависеть от веб-автоконфигурации и тестироваться без Spring.
 */
@Component
public class HtmlReportWriter implements ReportWriter {

    private final SpringTemplateEngine engine;
    private final LegalProperties legal;

    public HtmlReportWriter(LegalProperties legal) {
        this.legal = legal;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        this.engine = new SpringTemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    @Override
    public String fileName() {
        return "report.html";
    }

    @Override
    public Path write(AuditResult result, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        Path file = outDir.resolve(fileName());
        Files.writeString(file, render(result), StandardCharsets.UTF_8);
        return file;
    }

    public String render(AuditResult result) {
        return render(result, null, null);
    }

    /**
     * Отчёт для веб-интерфейса: сверху появляется панель со ссылками. В файловом отчёте её нет.
     *
     * @param backUrl ссылка «Новая проверка» или null
     * @param jsonUrl ссылка на report.json или null
     */
    public String render(AuditResult result, String backUrl, String jsonUrl) {
        Context context = new Context(Locale.forLanguageTag("ru"));
        context.setVariable("report", ReportView.of(result, legal));
        context.setVariable("backUrl", backUrl);
        context.setVariable("jsonUrl", jsonUrl);
        return engine.process("report", context);
    }
}
