package com.project.services;

import com.project.dto.requests.LoginRequest;
import com.project.dto.requests.RegisterRequest;
import com.project.dto.requests.ResetPasswordRequest;
import com.project.exceptions.AuthException;
import com.project.models.User;
import com.project.models.enums.Role;
import com.project.utils.SessionUtils;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();
    private String dummyHash;

    @Value("${app.session.cookie-name}")
    private String sessionCookieName;

    @Value("${app.session.duration-minutes}")
    private long sessionDurationMinutes;

    @Value("${app.session.http-only}")
    private boolean httpOnly;

    @Value("${app.session.secure}")
    private boolean secure;

    @Value("${app.session.same-site}")
    private String sameSite;

    @Value("${app.security.max-failed-login-attempts}")
    private int maxFailedLoginAttempts;

    @Value("${app.security.lock-duration-minutes}")
    private long lockDurationMinutes;

    @Value("${app.security.reset-token-duration-minutes}")
    private long resetTokenDurationMinutes;

    @PostConstruct
    public void initialize() {
        dummyHash = passwordEncoder.encode("TemporaryDummyPassword123!");
    }

    @Transactional
    public Map<String, Object> register(RegisterRequest request, HttpServletResponse response) {
        String email = normalizeEmail(request.getEmail());
        validatePasswordPolicy(request.getPassword());

        if (userService.findByEmail(email).isPresent()) {
            throw new AuthException("Unable to complete registration");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.ANALYST);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        establishSession(user);
        try {
            userService.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new AuthException("Unable to complete registration");
        }

        addSessionCookie(response, user.getSessionToken());
        return toAuthResponse(user);
    }

    @Transactional(noRollbackFor = AuthException.class)
    public Map<String, Object> login(LoginRequest request, HttpServletResponse response) {
        String email = normalizeEmail(request.getEmail());
        User user = userService.findByEmail(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.getPassword(), dummyHash);
            throw new AuthException("Invalid credentials");
        }

        userService.releaseExpiredLock(user);
        if (userService.isLockActive(user)) {
            throw new AuthException("Invalid credentials");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            registerFailedLogin(user);
            userService.save(user);
            throw new AuthException("Invalid credentials");
        }

        user.setFailedLoginAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        establishSession(user);
        userService.save(user);

        addSessionCookie(response, user.getSessionToken());
        return toAuthResponse(user);
    }

    public Map<String, Object> me() {
        return toAuthResponse(userService.requireCurrentUser());
    }

    @Transactional
    public Map<String, Object> forgotPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        User user = userService.findByEmail(email).orElse(null);
        if (user == null) {
            passwordEncoder.matches("TemporaryDummyPassword123!", dummyHash);
        } else {
            user.setResetPasswordToken(generateToken(32));
            user.setResetPasswordTokenExpiresAt(Instant.now().plus(Duration.ofMinutes(resetTokenDurationMinutes)));
            userService.save(user);
            emailService.sendPasswordResetMessage(user.getEmail(), user.getResetPasswordToken());
        }
        return Map.of("message", "If the account exists, reset instructions were sent.");
    }

    @Transactional
    public Map<String, Object> resetPassword(ResetPasswordRequest request) {
        String token = requiredValue(request.getToken(), "Reset token is required");
        String newPassword = requiredValue(request.getNewPassword(), "New password is required");
        validatePasswordPolicy(newPassword);

        User user = userService.findByResetToken(token)
                .orElseThrow(() -> new AuthException("Invalid or expired reset token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiresAt(null);
        user.setFailedLoginAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        userService.save(user);

        return Map.of("message", "Password updated successfully");
    }

    @Transactional
    public Map<String, Object> logout(HttpServletResponse response) {
        User user = userService.requireCurrentUser();
        userService.clearSession(user);
        userService.save(user);

        ResponseCookie clearedCookie = SessionUtils.clearSessionCookie(sessionCookieName, httpOnly, secure, sameSite);
        response.addHeader(HttpHeaders.SET_COOKIE, clearedCookie.toString());
        return Map.of("message", "Logged out");
    }

    private void establishSession(User user) {
        Instant issuedAt = Instant.now();
        user.setSessionToken(generateToken(48));
        user.setSessionIssuedAt(issuedAt);
        user.setSessionExpiresAt(issuedAt.plus(Duration.ofMinutes(sessionDurationMinutes)));
    }

    private void addSessionCookie(HttpServletResponse response, String token) {
        ResponseCookie cookie = SessionUtils.buildSessionCookie(
                sessionCookieName,
                token,
                Duration.ofMinutes(sessionDurationMinutes),
                httpOnly,
                secure,
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
        response.put("lockedUntil", user.getLockedUntil());
        response.put("sessionIssuedAt", user.getSessionIssuedAt());
        response.put("sessionExpiresAt", user.getSessionExpiresAt());
        return response;
    }

    private void registerFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= maxFailedLoginAttempts) {
            user.setLocked(true);
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(lockDurationMinutes)));
            userService.clearSession(user);
        }
    }

    private void validatePasswordPolicy(String password) {
        String value = requiredValue(password, "Password is required");
        if (value.length() < 12) {
            throw new IllegalArgumentException("Password must contain at least 12 characters");
        }
        if (value.chars().noneMatch(Character::isUpperCase)
                || value.chars().noneMatch(Character::isLowerCase)
                || value.chars().noneMatch(Character::isDigit)
                || value.chars().allMatch(Character::isLetterOrDigit)) {
            throw new IllegalArgumentException("Password must include upper, lower, numeric and special characters");
        }
    }

    private String normalizeEmail(String email) {
        String value = requiredValue(email, "Email is required").trim().toLowerCase();
        if (!value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new IllegalArgumentException("Invalid email address");
        }
        return value;
    }

    private String requiredValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String generateToken(int size) {
        byte[] buffer = new byte[size];
        secureRandom.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
