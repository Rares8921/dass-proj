package com.project.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.dto.responses.ErrorResponse;
import com.project.models.User;
import com.project.services.AuditService;
import com.project.services.UserService;
import com.project.utils.SessionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        if (!path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest wrappedRequest = request instanceof CachedBodyHttpServletRequest cached
                ? cached
                : new CachedBodyHttpServletRequest(request);
        String ipAddress = SessionUtils.resolveClientIp(request);
        Map<String, String> body = extractBody(wrappedRequest.bodyAsString());

        if ("/api/auth/login".equals(path)) {
            String email = Optional.ofNullable(body.get("email")).orElse("").trim().toLowerCase();
            if (!rateLimitingService.allowLoginFromIp(ipAddress) || !rateLimitingService.allowLoginForAccount(email)) {
                recordRateLimit(userService.findByEmail(email).orElse(null), "LOGIN_RATE_LIMITED", "auth", null, ipAddress);
                writeRateLimitError(response, path);
                return;
            }
        } else if ("/api/auth/register".equals(path)) {
            if (!rateLimitingService.allowRegistrationFromIp(ipAddress)) {
                recordRateLimit(null, "REGISTER_RATE_LIMITED", "auth", null, ipAddress);
                writeRateLimitError(response, path);
                return;
            }
        } else if ("/api/auth/forgot-password".equals(path)) {
            if (!rateLimitingService.allowForgotPasswordFromIp(ipAddress)) {
                String email = Optional.ofNullable(body.get("email")).orElse("").trim().toLowerCase();
                recordRateLimit(userService.findByEmail(email).orElse(null), "FORGOT_PASSWORD_RATE_LIMITED", "auth", null, ipAddress);
                writeRateLimitError(response, path);
                return;
            }
        } else if ("/api/auth/reset-password".equals(path)) {
            if (!rateLimitingService.allowResetFromIp(ipAddress)) {
                recordRateLimit(null, "RESET_PASSWORD_RATE_LIMITED", "auth", null, ipAddress);
                writeRateLimitError(response, path);
                return;
            }
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private Map<String, String> extractBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private void recordRateLimit(User user, String action, String resource, String resourceId, String ipAddress) {
        auditService.record(user, action, resource, resourceId, ipAddress);
    }

    private void writeRateLimitError(HttpServletResponse response, String path) throws IOException {
        ErrorResponse errorResponse = new ErrorResponse();
        errorResponse.setTimestamp(Instant.now());
        errorResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        errorResponse.setError(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase());
        errorResponse.setMessage("Too many requests");
        errorResponse.setPath(path);
        errorResponse.setDetails(List.of());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
