package ru.siteaudit.siteaudit.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.siteaudit.siteaudit.audit.AuditService;
import ru.siteaudit.siteaudit.model.AuditResult;
import ru.siteaudit.siteaudit.model.Finding;
import ru.siteaudit.siteaudit.model.Severity;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Веб-сценарий целиком на настоящем контексте; вместо обхода сайта браузером — заглушка AuditService. */
@SpringBootTest
@AutoConfigureMockMvc
class WebControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditService auditService;

    @Test
    void startPageHasForm() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"url\"")))
                .andExpect(content().string(containsString("Проверить сайт")));
    }

    @Test
    void rejectsInvalidAddressWithoutStartingAudit() throws Exception {
        mvc.perform(post("/audits").param("url", "ftp://example.ru"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Не похоже на адрес сайта")))
                .andExpect(content().string(containsString("value=\"ftp://example.ru\"")));
        verify(auditService, never()).audit(any(), any());
    }

    @Test
    void submitThenProgressThenReport() throws Exception {
        Finding finding = new Finding("https", Severity.CRITICAL, "Сайт работает без шифрования (HTTP)", "d",
                "http://shop.example/", "e", "pdn-security", "rec");
        AuditResult result = new AuditResult("https://shop.example", Instant.EPOCH, Instant.EPOCH,
                List.of("https://shop.example/"), List.of(finding), List.of());
        when(auditService.audit(eq("https://shop.example"), any())).thenAnswer(inv -> {
            Consumer<String> onPage = inv.getArgument(1);
            onPage.accept("https://shop.example/");
            return new AuditService.Audit(result, List.of());
        });

        MvcResult submitted = mvc.perform(post("/audits").param("url", "shop.example"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String jobUrl = submitted.getResponse().getRedirectedUrl();
        assertThat(jobUrl).startsWith("/audits/");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get(jobUrl + "/status"))
                        .andExpect(jsonPath("$.status").value("DONE"))
                        .andExpect(jsonPath("$.pagesLoaded").value(1))
                        .andExpect(jsonPath("$.reportUrl").value(jobUrl + "/report")));

        mvc.perform(get(jobUrl)).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", jobUrl + "/report"));

        mvc.perform(get(jobUrl + "/report"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Отчёт об автоматической проверке сайта")))
                .andExpect(content().string(containsString("Сайт работает без шифрования (HTTP)")))
                .andExpect(content().string(containsString("Новая проверка")));

        mvc.perform(get(jobUrl + "/report.json"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("report.json")))
                .andExpect(jsonPath("$.findings[0].ruleId").value("https"));

        mvc.perform(get("/"))
                .andExpect(content().string(containsString("https://shop.example")))
                .andExpect(content().string(containsString("Важно: 1")));
    }

    @Test
    void unknownJobIs404() throws Exception {
        mvc.perform(get("/audits/nope")).andExpect(status().isNotFound());
        mvc.perform(get("/audits/nope/status")).andExpect(status().isNotFound());
    }
}
