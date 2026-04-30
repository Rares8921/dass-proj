package com.project.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.security.reset-link-base-url}")
    private String resetLinkBaseUrl;

    public void sendPasswordResetMessage(String email, String token) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(email);
        message.setSubject("AuthX password reset");
        message.setText("""
                A password reset was requested for your AuthX account.

                Reset link: %s?token=%s
                Reset token: %s

                This token expires in 15 minutes and can be used only once.
                """.formatted(resetLinkBaseUrl, token, token));
        mailSender.send(message);
    }
}
