package com.project.dto.requests;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {

    @Email
    @Size(max = 320)
    private String email;

    @Size(max = 128)
    private String token;

    @Size(max = 255)
    private String newPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
