package com.project.services;

import com.project.dto.requests.LoginRequest;
import com.project.dto.requests.RegisterRequest;
import com.project.dto.requests.ResetPasswordRequest;
import com.project.exceptions.AuthException;
import com.project.models.User;
import com.project.models.enums.Role;
import com.project.repository.UserRepository;
import com.project.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final AuditService auditService;

    @Value("${app.session.cookie-name}")
    private String sessionCookieName;

    @Value("${app.session.same-site}")
    private String sameSite;

    @Transactional
    public Map<String, Object> register(RegisterRequest request,
                                        HttpServletRequest httpRequest,
                                        HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AuthException("User already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(md5(request.getPassword()));
        user.setRole(request.getRole() == null ? Role.ANALYST : request.getRole());
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        user.setSessionToken(UUID.randomUUID().toString());
        user.setSessionExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        userRepository.save(user);

        addSessionCookie(httpResponse, user.getSessionToken());
        auditService.record(user, "REGISTER", "auth", user.getId().toString(), httpRequest);
        return toAuthResponse(user);
    }

    @Transactional
    public Map<String, Object> login(LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AuthException("User does not exist"));

        String candidatePasswordHash = md5(request.getPassword());
        if (!candidatePasswordHash.equals(user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            userRepository.save(user);
            auditService.record(user, "LOGIN_FAILURE", "auth", user.getId().toString(), httpRequest);
            throw new AuthException("Invalid password");
        }

        user.setFailedLoginAttempts(0);
        user.setSessionToken(UUID.randomUUID().toString());
        user.setSessionExpiresAt(Instant.now().plus(Duration.ofDays(30)));
        userRepository.save(user);

        addSessionCookie(httpResponse, user.getSessionToken());
        auditService.record(user, "LOGIN_SUCCESS", "auth", user.getId().toString(), httpRequest);
        return toAuthResponse(user);
    }

    public Map<String, Object> me(HttpServletRequest request) {
        User user = userService.requireCurrentUser(request);
        return toAuthResponse(user);
    }

    @Transactional
    public Map<String, Object> forgotPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String email = normalizeEmail(request.getEmail());
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new AuthException("User does not exist"));

        String token = predictableResetToken(email);
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiresAt(null);
        userRepository.save(user);

        auditService.record(user, "FORGOT_PASSWORD", "auth", user.getId().toString(), httpRequest);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Reset token generated");
        response.put("token", token);
        return response;
    }

    @Transactional
    public Map<String, Object> resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        User user = userService.findByResetToken(request.getToken())
                .orElseThrow(() -> new AuthException("Invalid reset token"));

        user.setPasswordHash(md5(request.getNewPassword()));
        userRepository.save(user);

        auditService.record(user, "RESET_PASSWORD", "auth", user.getId().toString(), httpRequest);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Password updated");
        response.put("token", user.getResetPasswordToken());
        return response;
    }

    @Transactional
    public Map<String, Object> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        User user = userService.requireCurrentUser(httpRequest);
        user.setSessionToken(null);
        user.setSessionExpiresAt(null);
        userRepository.save(user);

        ResponseCookie clearedCookie = SessionUtils.clearSessionCookie(sessionCookieName, false, false, sameSite);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, clearedCookie.toString());
        auditService.record(user, "LOGOUT", "auth", user.getId().toString(), httpRequest);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Logged out");
        return response;
    }

    private void addSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = SessionUtils.buildSessionCookie(
                sessionCookieName,
                token,
                Duration.ofDays(30),
                false,
                false,
                sameSite
        );
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private Map<String, Object> toAuthResponse(User user) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("locked", user.isLocked());
        response.put("failedLoginAttempts", user.getFailedLoginAttempts());
        response.put("sessionExpiresAt", user.getSessionExpiresAt());
        return response;
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AuthException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    private String predictableResetToken(String email) {
        return md5(email + ":reset");
    }

    private String md5(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            byte[] digest = messageDigest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm is not available", exception);
        }
    }
}
