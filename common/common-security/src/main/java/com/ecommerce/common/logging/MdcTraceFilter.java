package com.ecommerce.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * MDC (Mapped Diagnostic Context) filter.
 *
 * Extracts the traceId from the incoming request header and puts it
 * in the logging context. Every subsequent log statement in the same
 * thread automatically includes the traceId.
 *
 * When you search Loki for traceId=abc123, you see EVERY log line
 * from every service that handled that request.
 *
 * The traceId propagates via:
 * - HTTP: W3C traceparent header (or X-B3-TraceId fallback)
 * - Kafka: embed as correlationId in event payload
 *
 * MUST clear MDC after the request — thread pool reuses threads.
 * Without the finally block, the next request picks up stale traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-B3-TraceId";
    private static final String TRACE_PARENT     = "traceparent";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String traceId = request.getHeader(TRACE_PARENT);
            if (traceId == null) {
                traceId = request.getHeader(TRACE_ID_HEADER);
            }
            if (traceId == null) {
                traceId = java.util.UUID.randomUUID().toString().replace("-", "");
            }

            MDC.put("traceId", traceId);

            String appName = getServletContext().getServletContextName();
            if (appName != null) MDC.put("service", appName);

            filterChain.doFilter(request, response);

        } finally {
            MDC.clear();
        }
    }
}
