package com.servicepros.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.servicepros.common.ApiError;
import com.servicepros.config.AppProperties;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Token-bucket rate limiting per client IP (Bucket4j) for login, token refresh and forgot / reset password. */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_KEYS = 50_000;

    private final AppProperties.RateLimit cfg;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitFilter(AppProperties props) {
        this.cfg = props.getRateLimit();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/auth/login") && !path.startsWith("/api/auth/refresh")
                && !path.startsWith("/api/auth/forgot-password") && !path.startsWith("/api/auth/reset-password")) {
            chain.doFilter(request, response);
            return;
        }

        if (buckets.size() > MAX_TRACKED_KEYS) {
            buckets.clear(); // crude memory guard; buckets refill quickly anyway
        }
        final int limit = cfg.getLoginPerMinute();
        Bucket bucket = buckets.computeIfAbsent(clientIp(request), k -> Bucket.builder()
                .addLimit(l -> l.capacity(limit).refillGreedy(limit, Duration.ofMinutes(1)))
                .build());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
        } else {
            long retry = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retry));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(ApiError.json(429, "Too Many Requests",
                    "Too many requests — please wait " + retry + " seconds and try again", path));
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (cfg.isTrustForwardedHeaders()) {
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
