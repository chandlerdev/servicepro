package com.servicepros.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.servicepros.common.ApiException;
import com.servicepros.notification.MailGateway;
import com.servicepros.notification.Notification;
import com.servicepros.notification.SmsGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgot password with an emailed 6-digit OTP, for Admins and Technicians (the Super Admin's password is set
 * on the server). Unknown, Super Admin and disabled accounts get a clear error under the email field.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID = "The code is invalid or has expired — request a new one";

    private final UserRepository users;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final MailGateway mail;

    /** Emails a new code (replacing any earlier one); 400 with an email field error when that isn't possible. */
    @Transactional
    public void requestCode(String email) {
        User user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> emailError("Email is not registered..!"));
        if (user.getRole() == User.Role.SUPER_ADMIN) {
            throw emailError("Password reset isn't available for the Super Admin account");
        }
        if (!user.isActive()) {
            throw emailError("This account is disabled — contact your administrator");
        }
        if (user.getRole() == User.Role.TECHNICIAN && user.getTechnician() != null
                && !users.findById(user.getTechnician().getOwnerId()).map(User::isActive).orElse(false)) {
            throw emailError("Your company account is disabled — contact your administrator");
        }
        codes.findFirstByUserIdAndUsedFalseOrderByIdDesc(user.getId())
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(Instant.now().minus(RESEND_COOLDOWN)))
                .ifPresent(c -> {
                    long wait = RESEND_COOLDOWN.minus(Duration.between(c.getCreatedAt(), Instant.now())).toSeconds();
                    throw ApiException.tooManyRequests("Please wait " + Math.max(1, wait) + " seconds before requesting another code");
                });
        codes.invalidateAllForUser(user.getId());

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        PasswordResetCode c = new PasswordResetCode();
        c.setUserId(user.getId());
        c.setCodeHash(hash(user.getId(), code));
        c.setExpiresAt(Instant.now().plus(CODE_TTL));
        codes.save(c);

        String body = "Hi " + user.getName() + ",\n\n"
                + "Your ServProWare password reset code is: " + code + "\n\n"
                + "It expires in " + CODE_TTL.toMinutes() + " minutes. If you didn't ask to reset your password, "
                + "you can ignore this email — your password stays the same.\n\n— ServProWare";
        SmsGateway.Result sent = mail.send(user.getEmail(), "Your ServProWare password reset code", body);
        if (sent.status() == Notification.Status.SIMULATED) {
            log.info("[Email simulated] password reset code for {}: {}", user.getEmail(), code);
        } else if (sent.status() == Notification.Status.FAILED) {
            throw ApiException.serviceUnavailable("We couldn't send the email right now — please try again in a few minutes");
        }
    }

    /** Checks the code and sets the new password; signs the user out of every device. */
    @Transactional(noRollbackFor = ApiException.class)
    public void reset(String email, String code, String newPassword) {
        User user = users.findByEmailIgnoreCase(email.trim())
                .filter(u -> u.getRole() != User.Role.SUPER_ADMIN && u.isActive())
                .orElseThrow(() -> ApiException.badRequest(INVALID));
        PasswordResetCode c = codes.findFirstByUserIdAndUsedFalseOrderByIdDesc(user.getId())
                .filter(x -> x.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> ApiException.badRequest(INVALID));
        if (!c.getCodeHash().equals(hash(user.getId(), code.trim()))) {
            c.setAttempts(c.getAttempts() + 1);
            int left = MAX_ATTEMPTS - c.getAttempts();
            if (left <= 0) {
                c.setUsed(true);
                throw ApiException.badRequest("Too many wrong codes — request a new one");
            }
            throw ApiException.badRequest("Incorrect code — " + left + (left == 1 ? " attempt" : " attempts") + " left");
        }
        c.setUsed(true);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForUser(user.getId());
    }

    @Scheduled(cron = "0 20 3 * * *")
    @Transactional
    public void purge() {
        codes.purge(Instant.now());
    }

    private static ApiException emailError(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message, Map.of("email", message));
    }

    private static String hash(Long userId, String code) {
        return AuthService.hash(userId + ":" + code);
    }
}
