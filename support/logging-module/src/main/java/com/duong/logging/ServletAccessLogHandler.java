package com.duong.logging;

import io.micrometer.observation.Observation;
import org.springframework.http.server.observation.ServerRequestObservationContext;

final class ServletAccessLogHandler extends AccessLogHandler<ServerRequestObservationContext> {
    ServletAccessLogHandler(String actuatorPath) {
        super(actuatorPath);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        // Micrometer selects handlers before a convention assigns the observation name.
        return context instanceof ServerRequestObservationContext;
    }

    @Override
    public void onStop(ServerRequestObservationContext context) {
        var request = context.getCarrier();
        var response = context.getResponse();
        if (request != null && response != null) {
            String path = request.getRequestURI();
            write(context, request.getMethod(), path,
                    path.substring(request.getContextPath().length()), finalStatus(context, response.getStatus()));
        }
    }
}
