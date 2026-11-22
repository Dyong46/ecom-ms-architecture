package com.duong.logging;

import io.micrometer.observation.Observation;
import org.springframework.http.server.reactive.observation.ServerRequestObservationContext;

final class ReactiveAccessLogHandler extends AccessLogHandler<ServerRequestObservationContext> {
    ReactiveAccessLogHandler(String actuatorPath) {
        super(actuatorPath);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        var request = context.getCarrier();
        var response = context.getResponse();
        if (request != null && response != null) {
            // A disconnect has no reliable HTTP response status: use 0, not a fake 200.
            int status = context.isConnectionAborted() ? 0
                    : response.getStatusCode() != null ? response.getStatusCode().value() : 200;
            write(context, request.getMethod().name(), request.getURI().getRawPath(),
                    request.getPath().pathWithinApplication().value(),
                    context.isConnectionAborted() ? status : finalStatus(context, status));
        }
    }
}
