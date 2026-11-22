package com.duong.logging;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.Map;

abstract class AccessLogHandler<C extends Observation.Context> implements ObservationHandler<C> {
    private static final Logger LOG = LoggerFactory.getLogger("http.access");
    private static final String START_NANOS = AccessLogHandler.class.getName() + ".startNanos";
    private final String actuatorPath;

    AccessLogHandler(String actuatorPath) {
        this.actuatorPath = actuatorPath;
    }

    @Override
    public void onStart(C context) {
        context.put(START_NANOS, System.nanoTime());
    }

    protected void write(C context, String method, String path, String applicationPath, int status) {
        if (applicationPath.equals(actuatorPath) || applicationPath.startsWith(actuatorPath + "/")) {
            return;
        }
        Long start = context.get(START_NANOS);
        if (start == null) {
            return;
        }
        double durationMs = (System.nanoTime() - start) / 1_000_000.0;
        // Read the observation's span, not a potentially unrelated reactive thread's MDC.
        TracingObservationHandler.TracingContext tracing = context.get(TracingObservationHandler.TracingContext.class);
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        try {
            MDC.remove("traceId");
            MDC.remove("spanId");
            if (tracing != null && tracing.getSpan() != null) {
                MDC.put("traceId", tracing.getSpan().context().traceId());
                MDC.put("spanId", tracing.getSpan().context().spanId());
            }
            Level level = status >= 500 ? Level.ERROR : status >= 400 ? Level.WARN : Level.INFO;
            LOG.atLevel(level)
                    .addKeyValue("event", "http_access")
                    .addKeyValue("method", method)
                    .addKeyValue("path", path)
                    .addKeyValue("status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .log("HTTP {} {} -> {}", method, path, status);
        } finally {
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
        }
    }

    protected int finalStatus(C context, int responseStatus) {
        // An unhandled exception can reach the observation before container error dispatch.
        return context.getError() != null && responseStatus < 400 ? 500 : responseStatus;
    }
}
