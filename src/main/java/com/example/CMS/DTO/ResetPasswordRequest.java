package com.example.CMS.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ResetPasswordRequest {
    /** Account to change; defaults to the caller. Only admins may name someone else. */
    private String username;

    @NotBlank
    @Size(min = 8, max = 72) // 72 bytes is BCrypt's limit
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
