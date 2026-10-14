package com.applymate.config;

import com.applymate.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    static final String DEFAULT_SECRET_PREFIX = "default-dev-secret";
    private static final String VERSION_CLAIM = "ver";

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Fail at startup, not on the first login.
            throw new IllegalStateException("jwt.secret must be at least 32 bytes for HS256");
        }
        if (jwtProperties.getSecret().startsWith(DEFAULT_SECRET_PREFIX)) {
            log.warn("Using the built-in development JWT secret. Set JWT_SECRET before deploying.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /** Parsed, signature-checked claims of a token. */
    public record TokenClaims(String email, int version) {}

    public String generateToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getExpirationMs());
        // HS256 explicitly: JJWT 0.12 otherwise infers HS512 from key length and
        // rejects secrets shorter than 64 bytes.
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(VERSION_CLAIM, user.getTokenVersion())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            Integer version = claims.get(VERSION_CLAIM, Integer.class);
            return Optional.of(new TokenClaims(claims.getSubject(), version == null ? 0 : version));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
