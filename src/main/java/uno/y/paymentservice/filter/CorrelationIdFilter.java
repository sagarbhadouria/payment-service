package uno.y.paymentservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // Try to get correlation ID from header
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);

            // If not present, generate a new one
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = generateCorrelationId();
            }

            // Put into MDC for logging
            MDC.put(CORRELATION_ID_KEY, correlationId);

            // Also set response header so client can use it
            response.setHeader(CORRELATION_ID_HEADER, correlationId);

            // Continue request processing
            filterChain.doFilter(request, response);

        } finally {
            // Clear MDC to avoid memory leaks and incorrect values in other requests
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private String generateCorrelationId() {
        return "req-" + UUID.randomUUID();
    }
}