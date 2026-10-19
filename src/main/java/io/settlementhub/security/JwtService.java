package io.settlementhub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.settlementhub.common.config.AppProperties;
import io.settlementhub.security.user.UserAccount;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_LENGTH_BYTES = 32;
    private static final long DEFAULT_TTL_SECONDS = 3600;

    private final SecretKey signingKey;
    private final long ttlSeconds;

    public JwtService(AppProperties properties) {
        AppProperties.Jwt jwt = properties.jwt();
        String secret = jwt == null ? null : jwt.secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "APP_JWT_SECRET must be set. Provide a secret of at least 32 bytes.");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_LENGTH_BYTES) {
            throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes long.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.ttlSeconds = jwt.ttlSeconds() > 0 ? jwt.ttlSeconds() : DEFAULT_TTL_SECONDS;
    }

    public String generateToken(UserAccount user) {
        Instant issuedAt = Instant.now();
        JwtBuilder builder =
                Jwts.builder()
                        .subject(user.getUsername())
                        .claim("role", user.getRole().name())
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(issuedAt.plusSeconds(ttlSeconds)))
                        .signWith(signingKey);
        if (user.getMerchantId() != null) {
            builder.claim("merchantId", user.getMerchantId().toString());
        }
        return builder.compact();
    }

    public JwtPrincipal parseToken(String token) {
        Claims claims =
                Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
        String merchantIdClaim = claims.get("merchantId", String.class);
        UUID merchantId = merchantIdClaim == null ? null : UUID.fromString(merchantIdClaim);
        return new JwtPrincipal(
                claims.getSubject(), Role.valueOf(claims.get("role", String.class)), merchantId);
    }

    public long expiresInSeconds() {
        return ttlSeconds;
    }

    public record JwtPrincipal(String username, Role role, UUID merchantId) {}
}
