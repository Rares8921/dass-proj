package com.project.services;

import com.project.exceptions.AuthException;
import com.project.models.User;
import com.project.models.enums.Role;
import com.project.repository.UserRepository;
import com.project.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.session.cookie-name}")
    private String sessionCookieName;

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
        return userRepository.findByResetPasswordToken(token.trim());
    }

    public User requireCurrentUser(HttpServletRequest request) {
        String sessionToken = SessionUtils.extractCookieValue(request, sessionCookieName)
                .orElseThrow(() -> new AuthException("Authentication required"));
        return userRepository.findBySessionToken(sessionToken)
                .orElseThrow(() -> new AuthException("Invalid session"));
    }

    public User requireManager(HttpServletRequest request) {
        User user = requireCurrentUser(request);
        if (user.getRole() != Role.MANAGER) {
            throw new AuthException("Manager role required");
        }
        return user;
    }

    public User requireById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new AuthException("User does not exist"));
    }
}
