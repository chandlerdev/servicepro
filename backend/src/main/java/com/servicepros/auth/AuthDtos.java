package com.servicepros.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(@NotBlank @Email String email,
                                       @NotBlank @Pattern(regexp = "^\\s*\\d{6}\\s*$", message = "must be the 6-digit code") String code,
                                       @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record MessageResponse(String message) {
    }

    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
                               UserResponse user) {
    }

    public record UserResponse(Long id, String name, String email, User.Role role, Long technicianId, boolean active) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getRole(),
                    u.getTechnician() == null ? null : u.getTechnician().getId(), u.isActive());
        }
    }
}
