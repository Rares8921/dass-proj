package com.project.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

public final class SessionUtils {

    private SessionUtils() {
    }

    public static ResponseCookie buildSessionCookie(String cookieName, String value, Duration duration, boolean httpOnly,
                                                    boolean secure, String sameSite) {
        return ResponseCookie.from(cookieName, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(duration)
                .build();
    }

    public static ResponseCookie clearSessionCookie(String cookieName, boolean httpOnly, boolean secure, String sameSite) {
        return ResponseCookie.from(cookieName, "")
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    public static Optional<String> extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    public static String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
