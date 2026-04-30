package com.project.bootstrap;

import com.project.models.User;
import com.project.models.enums.Role;
import com.project.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
public class DataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final String managerEmail;
    private final String managerPassword;
    private final String analystEmail;
    private final String analystPassword;
    private final String passwordMode;

    public DataInitializer(UserRepository userRepository,
                           @Value("${app.bootstrap.manager.email}") String managerEmail,
                           @Value("${app.bootstrap.manager.password}") String managerPassword,
                           @Value("${app.bootstrap.analyst.email}") String analystEmail,
                           @Value("${app.bootstrap.analyst.password}") String analystPassword,
                           @Value("${app.bootstrap.password-mode:MD5}") String passwordMode) {
        this.userRepository = userRepository;
        this.managerEmail = managerEmail;
        this.managerPassword = managerPassword;
        this.analystEmail = analystEmail;
        this.analystPassword = analystPassword;
        this.passwordMode = passwordMode;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createUserIfMissing(managerEmail, managerPassword, Role.MANAGER);
        createUserIfMissing(analystEmail, analystPassword, Role.ANALYST);
    }

    private void createUserIfMissing(String email, String rawPassword, Role role) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }
        User user = new User();
        user.setEmail(email.trim().toLowerCase());
        user.setPasswordHash(encodePassword(rawPassword));
        user.setRole(role);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
    }

    private String encodePassword(String rawPassword) {
        String mode = passwordMode == null ? "MD5" : passwordMode.trim().toUpperCase();
        if ("PLAIN".equals(mode)) {
            return rawPassword;
        }
        if ("MD5".equals(mode)) {
            return md5(rawPassword);
        }
        throw new IllegalStateException("Unsupported: " + passwordMode);
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
            throw new IllegalStateException("MD5 is not available", exception);
        }
    }
}
