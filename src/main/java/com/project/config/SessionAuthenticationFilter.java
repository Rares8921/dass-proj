package com.project.config;

import com.project.models.User;
import com.project.services.AuditService;
import com.project.utils.SessionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final com.project.services.UserService userService;
    private final AuditService auditService;

    @Value("${app.session.cookie-name}")
    private String sessionCookieName;

    @Value("${app.session.http-only}")
    private boolean httpOnly;

    @Value("${app.session.secure}")
    private boolean secure;

    @Value("${app.session.same-site}")
    private String sameSite;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        SecurityContextHolder.clearContext();

        SessionUtils.extractCookieValue(request, sessionCookieName)
                .ifPresentOrElse(token -> handleSessionToken(token, request, response), () -> {
                });

        filterChain.doFilter(request, response);
    }

    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                user,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void clearExpiredCookieIfPresent(HttpServletRequest request, HttpServletResponse response) {
        if (SessionUtils.extractCookieValue(request, sessionCookieName).isEmpty()) {
            return;
        }
        ResponseCookie clearedCookie = SessionUtils.clearSessionCookie(sessionCookieName, httpOnly, secure, sameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie.toString());
    }

    private void handleSessionToken(String token, HttpServletRequest request, HttpServletResponse response) {
        userService.findByActiveSessionToken(token).ifPresentOrElse(this::authenticate, () -> invalidateSessionToken(token, request, response));
    }

    private void invalidateSessionToken(String token, HttpServletRequest request, HttpServletResponse response) {
        userService.findBySessionToken(token).ifPresent(user -> {
            userService.clearSession(user);
            userService.save(user);
            auditService.record(user, "SESSION_INVALIDATED", "auth", user.getId().toString(), SessionUtils.resolveClientIp(request));
        });
        clearExpiredCookieIfPresent(request, response);
    }
}
