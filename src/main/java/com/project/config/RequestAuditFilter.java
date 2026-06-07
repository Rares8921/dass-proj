package com.project.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.models.Ticket;
import com.project.models.User;
import com.project.services.AuditService;
import com.project.services.UserService;
import com.project.utils.SessionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RequestAuditFilter extends OncePerRequestFilter {

    private static final int MAX_CAPTURE_LENGTH = 16000;
    private static final Set<String> SENSITIVE_AUTH_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/forgot-password",
            "/api/auth/reset-password"
    );

    private final AuditService auditService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Value("${app.session.cookie-name}")
    private String sessionCookieName;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CachedBodyHttpServletRequest wrappedRequest = request instanceof CachedBodyHttpServletRequest cached
                ? cached
                : new CachedBodyHttpServletRequest(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            try {
                auditRequest(wrappedRequest, wrappedResponse, startedAt);
            } finally {
                wrappedResponse.copyBodyToResponse();
            }
        }
    }

    private void auditRequest(CachedBodyHttpServletRequest request,
                              ContentCachingResponseWrapper response,
                              long startedAt) {
        User user = resolveUser(request);
        String responseBody = truncate(readResponseBody(request, response));
        String resource = resolveResource(request);
        String resourceId = resolveResourceId(request, responseBody);
        Ticket ticket = resolveTicket(resourceId).orElse(null);

        auditService.recordHttpRequest(
                user,
                ticket,
                "HTTP_REQUEST",
                resource,
                resourceId,
                SessionUtils.resolveClientIp(request),
                request,
                response,
                toJson(readHeaders(request)),
                toJson(readCookies(request.getCookies())),
                toJson(readParameters(request)),
                truncate(readRequestBody(request)),
                toJson(readHeaders(response)),
                responseBody,
                toJson(readRequestFlags(request, user)),
                toJson(readResponseFlags(response)),
                System.currentTimeMillis() - startedAt
        );
    }

    private User resolveUser(HttpServletRequest request) {
        try {
            return userService.requireCurrentUser();
        } catch (Exception exception) {
            return SessionUtils.extractCookieValue(request, sessionCookieName)
                    .flatMap(userService::findBySessionToken)
                    .orElse(null);
        }
    }

    private Optional<Ticket> resolveTicket(String resourceId) {
        if (resourceId == null || resourceId.isBlank()) {
            return Optional.empty();
        }
        try {
            return auditService.findTicketById(UUID.fromString(resourceId));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private String resolveResource(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth")) {
            return "auth";
        }
        if (uri.startsWith("/api/tickets")) {
            return "ticket";
        }
        if (uri.startsWith("/api/audit")) {
            return "audit";
        }
        return "http";
    }

    private String resolveResourceId(HttpServletRequest request, String responseBody) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/tickets/")) {
            String[] segments = uri.split("/");
            String candidate = segments[segments.length - 1];
            if (!candidate.isBlank() && !"search".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && "/api/tickets".equals(uri)) {
            return readJsonField(responseBody, "id");
        }
        return null;
    }

    private Map<String, List<String>> readHeaders(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            List<String> values = new ArrayList<>();
            request.getHeaders(name).asIterator().forEachRemaining(values::add);
            headers.put(name, values);
        });
        return headers;
    }

    private Map<String, List<String>> readHeaders(ContentCachingResponseWrapper response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.getHeaderNames()) {
            headers.put(name, List.copyOf(response.getHeaders(name)));
        }
        return headers;
    }

    private Map<String, Object> readCookies(Cookie[] cookies) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (cookies == null) {
            return values;
        }
        for (Cookie cookie : cookies) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("value", cookie.getValue());
            metadata.put("domain", cookie.getDomain());
            metadata.put("path", cookie.getPath());
            metadata.put("maxAge", cookie.getMaxAge());
            metadata.put("httpOnly", cookie.isHttpOnly());
            metadata.put("secure", cookie.getSecure());
            values.put(cookie.getName(), metadata);
        }
        return values;
    }

    private Map<String, List<String>> readParameters(HttpServletRequest request) {
        Map<String, List<String>> parameters = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, value) -> parameters.put(key, Arrays.asList(value)));
        return parameters;
    }

    private Map<String, Object> readRequestFlags(HttpServletRequest request, User user) {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("protocol", request.getProtocol());
        flags.put("scheme", request.getScheme());
        flags.put("serverName", request.getServerName());
        flags.put("serverPort", request.getServerPort());
        flags.put("secure", request.isSecure());
        flags.put("asyncStarted", request.isAsyncStarted());
        flags.put("asyncSupported", request.isAsyncSupported());
        flags.put("dispatcherType", String.valueOf(request.getDispatcherType()));
        flags.put("characterEncoding", request.getCharacterEncoding());
        flags.put("contentLength", request.getContentLengthLong());
        flags.put("contentType", request.getContentType());
        flags.put("locale", request.getLocale() == null ? null : request.getLocale().toLanguageTag());
        flags.put("localAddress", request.getLocalAddr());
        flags.put("localPort", request.getLocalPort());
        flags.put("remoteAddress", request.getRemoteAddr());
        flags.put("remoteHost", request.getRemoteHost());
        flags.put("remotePort", request.getRemotePort());
        flags.put("requestedSessionId", request.getRequestedSessionId());
        flags.put("requestedSessionIdFromCookie", request.isRequestedSessionIdFromCookie());
        flags.put("requestedSessionIdFromURL", request.isRequestedSessionIdFromURL());
        flags.put("requestedSessionIdValid", request.isRequestedSessionIdValid());
        flags.put("hasCsrfHeader", request.getHeader("X-XSRF-TOKEN") != null);
        flags.put("hasSessionCookie", SessionUtils.extractCookieValue(request, sessionCookieName).isPresent());
        flags.put("authenticatedUserPresent", user != null);
        return flags;
    }

    private Map<String, Object> readResponseFlags(ContentCachingResponseWrapper response) {
        Map<String, Object> flags = new LinkedHashMap<>();
        flags.put("status", response.getStatus());
        flags.put("successful", response.getStatus() >= 200 && response.getStatus() < 400);
        flags.put("committed", response.isCommitted());
        flags.put("bufferSize", response.getBufferSize());
        flags.put("characterEncoding", response.getCharacterEncoding());
        flags.put("contentType", response.getContentType());
        flags.put("containsSetCookie", response.containsHeader(HttpHeaders.SET_COOKIE));
        flags.put("containsLocation", response.containsHeader(HttpHeaders.LOCATION));
        flags.put("contentLength", response.getContentSize());
        return flags;
    }

    private String readRequestBody(CachedBodyHttpServletRequest request) {
        if (isSensitiveAuthPath(request)) {
            return "[sensitive auth request body omitted]";
        }
        String body = request.bodyAsString();
        if (body == null || body.isBlank()) {
            return "";
        }
        if (request.getContentType() != null && request.getContentType().startsWith("multipart/")) {
            return "[multipart payload omitted from preview]";
        }
        return body;
    }

    private String readResponseBody(HttpServletRequest request, ContentCachingResponseWrapper response) {
        if (request.getRequestURI().startsWith("/api/audit")) {
            return "[audit response omitted from recursive capture]";
        }
        if (isSensitiveAuthPath(request)) {
            return "[sensitive auth response body omitted]";
        }
        byte[] body = response.getContentAsByteArray();
        if (body.length == 0) {
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private boolean isSensitiveAuthPath(HttpServletRequest request) {
        return SENSITIVE_AUTH_PATHS.contains(request.getRequestURI());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return "{\"serializationError\":true}";
        }
    }

    private String readJsonField(String payload, String field) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(payload, Map.class);
            Object value = map.get(field);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_CAPTURE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CAPTURE_LENGTH) + "...[truncated]";
    }
}
