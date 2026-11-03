package com.financialtransaction.security.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int REQUEST_LIMIT = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ConcurrentMap<String, Bucket> buckets =
            new ConcurrentHashMap<>();

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
                REQUEST_LIMIT,
                Refill.intervally(REQUEST_LIMIT, WINDOW)
        );

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Override
    protected boolean shouldNotFilter(
            @NonNull HttpServletRequest request
    ) {
        String path = request.getServletPath();

        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/swagger-ui.html")
                || request.getMethod().equalsIgnoreCase("OPTIONS");
    }


    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String clientKey = request.getRemoteAddr();

        Bucket bucket = buckets.computeIfAbsent(
                clientKey,
                key -> newBucket()
        );

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", "60");

        response.getWriter().write(
                "{\"status\":429,"
                        + "\"error\":\"Too Many Requests\","
                        + "\"message\":\"Rate limit exceeded. Please try again later.\"}"
        );
    }
}

