package com.servicepros.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

import com.servicepros.auth.AuthDtos.AuthResponse;
import com.servicepros.auth.AuthDtos.UserResponse;
import com.servicepros.common.ApiException;
import com.servicepros.config.AppProperties;
import com.servicepros.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties props;

    @Transactional
    public AuthResponse login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "No account found with this email",
                        Map.of("email", "No account found with this email")));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect password",
                    Map.of("password", "Incorrect password"));
        }
        ensureActive(user);
        return issue(user);
    }

    /** noRollbackFor: the "revoke all sessions on token reuse" write must survive the 401. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse refresh(String rawToken) {
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (token.isRevoked()) {
            // Reuse of a rotated token → likely theft; kill every session of this user.
            refreshTokens.revokeAllForUser(token.getUser().getId());
            throw ApiException.unauthorized("Refresh token has been revoked");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token expired");
        }
        User user = token.getUser();
        ensureActive(user);
        token.setRevoked(true);
        return issue(user);
    }

    /** The account must be active — and a technician's admin too (disabling an admin locks out their team). */
    private void ensureActive(User user) {
        if (!user.isActive()) {
            throw ApiException.unauthorized("This account is disabled");
        }
        if (user.getRole() == User.Role.TECHNICIAN && user.getTechnician() != null) {
            boolean adminActive = users.findById(user.getTechnician().getOwnerId()).map(User::isActive).orElse(false);
            if (!adminActive) {
                throw ApiException.unauthorized("Your company account is disabled — contact your administrator");
            }
        }
    }

    @Transactional
    public void logout(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(t -> t.setRevoked(true));
    }

    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return UserResponse.from(users.findById(userId).orElseThrow(() -> ApiException.unauthorized("User not found")));
    }

    @Transactional
    public void changePassword(Long userId, String current, String next) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("User not found"));
        if (!passwordEncoder.matches(current, user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(next));
        refreshTokens.revokeAllForUser(userId);
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredTokens() {
        refreshTokens.purge(Instant.now());
    }

    private AuthResponse issue(User user) {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hash(raw));
        rt.setExpiresAt(Instant.now().plus(props.getJwt().getRefreshTtl()));
        refreshTokens.save(rt);

        return new AuthResponse(jwtService.createAccessToken(user), raw, "Bearer", jwtService.accessTtlSeconds(),
                UserResponse.from(user));
    }

    static String hash(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
