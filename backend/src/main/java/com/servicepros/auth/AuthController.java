package com.servicepros.auth;

import com.servicepros.auth.AuthDtos.AuthResponse;
import com.servicepros.auth.AuthDtos.ChangePasswordRequest;
import com.servicepros.auth.AuthDtos.ForgotPasswordRequest;
import com.servicepros.auth.AuthDtos.LoginRequest;
import com.servicepros.auth.AuthDtos.MessageResponse;
import com.servicepros.auth.AuthDtos.RefreshRequest;
import com.servicepros.auth.AuthDtos.ResetPasswordRequest;
import com.servicepros.auth.AuthDtos.UserResponse;
import com.servicepros.security.CurrentUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.refreshToken());
    }

    /** Step 1 of "forgot password": emails a 6-digit code (400 "Email is not registered..!" for unknown emails). */
    @PostMapping("/forgot-password")
    public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordResetService.requestCode(req.email());
        return new MessageResponse("A 6-digit code has been sent to " + req.email().trim() + ". It expires in 10 minutes.");
    }

    /** Step 2: checks the code and sets the new password (signs out every device). */
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordResetService.reset(req.email(), req.code(), req.newPassword());
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.me(CurrentUser.get().id());
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(CurrentUser.get().id(), req.currentPassword(), req.newPassword());
    }
}
