package com.duong.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

abstract class AccessLogHttpAssertions {
    @LocalServerPort
    int port;

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger("http.access");
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void capture() {
        appender = new ListAppender<>() {
            @Override
            protected void append(ILoggingEvent event) {
                event.prepareForDeferredProcessing();
                super.append(event);
            }
        };
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void release() {
        accessLogger.detachAppender(appender);
        appender.stop();
    }

    @ParameterizedTest
    @CsvSource({"/ok,200,INFO", "/does-not-exist,404,WARN", "/unauthorized,401,WARN", "/blocked,403,WARN",
            "/not-implemented,501,ERROR", "/failure,500,ERROR", "/async,200,INFO"})
    void recordsOneCompletedExchangeWithFinalStatus(String path, int expectedStatus, String expectedLevel) throws Exception {
        assertThat(request(path).statusCode()).isEqualTo(expectedStatus);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(appender.list).hasSize(1));
        ILoggingEvent event = appender.list.get(0);
        Map<String, Object> fields = fields(event);
        assertThat(fields).containsEntry("event", "http_access")
                .containsEntry("method", "GET").containsEntry("path", path).containsEntry("status", expectedStatus);
        assertThat(((Number) fields.get("duration_ms")).doubleValue()).isGreaterThanOrEqualTo(0);
        assertThat(event.getLevel().toString()).isEqualTo(expectedLevel);
        assertThat(event.getMDCPropertyMap().get("traceId")).matches("[0-9a-f]{16,32}");
        assertThat(event.getMDCPropertyMap().get("spanId")).matches("[0-9a-f]{16}");
        // In particular, async processing and Servlet error dispatch must not log twice.
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(appender.list).hasSize(1));
    }

    @Test
    void doesNotLogQueryParametersOrHeadersAndKeepsIncomingTrace() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ok?client_secret=DUMMY_QUERY"))
                .header("Authorization", "Bearer DUMMY_TOKEN")
                .header("Cookie", "session=DUMMY_COOKIE")
                .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
                .GET().build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(appender.list).hasSize(1));
        ILoggingEvent event = appender.list.get(0);
        assertThat(fields(event)).containsEntry("path", "/ok");
        assertThat(event.getFormattedMessage()).doesNotContain("DUMMY", "client_secret", "?");
        assertThat(fields(event).toString()).doesNotContain("DUMMY");
        assertThat(event.getMDCPropertyMap().get("traceId")).isEqualTo("0123456789abcdef0123456789abcdef");
    }

    @Test
    void skipsActuatorPolling() throws Exception {
        assertThat(request("/actuator/health").statusCode()).isEqualTo(200);
        await().during(Duration.ofMillis(150)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(appender.list).isEmpty());
    }

    @Test
    void doesNotCopyRequestBodyToAccessLog() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/body"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("DUMMY_PRIVATE_BODY")).build();
        assertThat(HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(appender.list).hasSize(1));
        ILoggingEvent event = appender.list.get(0);
        assertThat(fields(event)).containsEntry("method", "POST").containsEntry("path", "/body");
        assertThat(event.getFormattedMessage()).doesNotContain("DUMMY_PRIVATE_BODY");
        assertThat(fields(event).toString()).doesNotContain("DUMMY_PRIVATE_BODY");
    }

    private HttpResponse<Void> request(String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.discarding());
    }

    private Map<String, Object> fields(ILoggingEvent event) {
        return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
    }
}
