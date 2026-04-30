package com.project.services;

import com.project.exceptions.AuthException;
import com.project.models.User;
import com.project.models.enums.Role;
import com.project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email.trim());
    }

    public Optional<User> findByResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByResetPasswordToken(token.trim())
                .filter(user -> user.getResetPasswordTokenExpiresAt() != null)
                .filter(user -> user.getResetPasswordTokenExpiresAt().isAfter(Instant.now()));
    }

    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> findBySessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findBySessionToken(token);
    }

    public Optional<User> findByActiveSessionToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findBySessionToken(token)
                .filter(user -> user.getSessionExpiresAt() != null)
                .filter(user -> user.getSessionExpiresAt().isAfter(Instant.now()));
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public User saveAndFlush(User user) {
        return userRepository.saveAndFlush(user);
    }

    public void clearSession(User user) {
        user.setSessionToken(null);
        user.setSessionIssuedAt(null);
        user.setSessionExpiresAt(null);
    }

    public boolean isLockActive(User user) {
        return user != null && user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    public void releaseExpiredLock(User user) {
        if (user == null || user.getLockedUntil() == null) {
            return;
        }
        if (user.getLockedUntil().isAfter(Instant.now())) {
            return;
        }
        user.setLocked(false);
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        save(user);
    }

    public User requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            throw new AuthException("Authentication required");
        }
        return user;
    }

    public User requireManager() {
        User user = requireCurrentUser();
        if (user.getRole() != Role.MANAGER) {
            throw new AuthException("Access denied");
        }
        return user;
    }

    public boolean isManager(User user) {
        return user != null && user.getRole() == Role.MANAGER;
    }
}
