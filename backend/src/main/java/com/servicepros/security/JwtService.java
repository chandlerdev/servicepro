package com.servicepros.security;

import java.util.Date;

import javax.crypto.SecretKey;

import com.servicepros.auth.User;
import com.servicepros.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class JwtService {

    private static final String ISSUER = "servicepros";

    private final SecretKey key;
    private final AppProperties props;

    /** Development-only key, used when JWT_SECRET is missing or blank. Never use it in production. */
    private static final String DEV_SECRET =
            "TWsd5tuEFYAc9VT2AjMV3bIsJpBBjOyepgy8RreSiNq0pn6NsHFFNMR6AJ1p0AyTeYRsRPY/hI3EiaguVZ+s5w==";

    public JwtService(AppProperties props) {
        this.props = props;
        String secret = props.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set — using the built-in development key. Set JWT_SECRET in production!");
            secret = DEV_SECRET;
        }
        byte[] bytes = Decoders.BASE64.decode(secret.trim());
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be a Base64 key of at least 256 bits");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createAccessToken(User user) {
        long now = System.currentTimeMillis();
        var builder = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + props.getJwt().getAccessTtl().toMillis()));
        if (user.getTechnician() != null) {
            builder.claim("tid", user.getTechnician().getId());
        }
        Long ownerId = ownerIdOf(user);
        if (ownerId != null) {
            builder.claim("oid", ownerId);
        }
        return builder.signWith(key).compact();
    }

    public long accessTtlSeconds() {
        return props.getJwt().getAccessTtl().toSeconds();
    }

    /** @throws JwtException when the token is invalid or expired */
    public AppUserPrincipal parse(String token) {
        Claims c = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build().parseSignedClaims(token).getPayload();
        Object tid = c.get("tid");
        Object oid = c.get("oid");
        Long id = Long.valueOf(c.getSubject());
        User.Role role = User.Role.valueOf(c.get("role", String.class));
        Long ownerId = null;
        if (oid != null) {
            ownerId = ((Number) oid).longValue();
        } else if (role == User.Role.ADMIN) {
            ownerId = id;
        }
        return new AppUserPrincipal(id, c.get("email", String.class), role,
                tid == null ? null : ((Number) tid).longValue(), ownerId);
    }

    /** Admin → own id; technician → the admin they belong to; super admin → none. */
    private static Long ownerIdOf(User user) {
        return switch (user.getRole()) {
            case ADMIN -> user.getId();
            case TECHNICIAN -> user.getTechnician() == null ? null : user.getTechnician().getOwnerId();
            case SUPER_ADMIN -> null;
        };
    }
}
