package com.applymate.service;

import com.applymate.config.JwtProperties;
import com.applymate.config.JwtService;
import com.applymate.dto.AuthResponse;
import com.applymate.error.ApiException;
import com.applymate.model.RefreshToken;
import com.applymate.model.User;
import com.applymate.repository.RefreshTokenRepository;
import com.applymate.repository.UserRepository;
import com.applymate.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Sessions: a short-lived access JWT plus a rotating refresh token.
 *
 * <p>Every refresh revokes the presented token and issues a new one in the same
 * family. If a token that was already rotated is presented again, someone holds a
 * copy of it (a stolen token), so the entire family is revoked and both the thief and
 * the real user must sign in again. That is the standard refresh-token reuse
 * detection from the OAuth 2.0 Security BCP.
 *
 * <p>One exception: a reuse within {@link #RACE_GRACE} of the rotation is almost always
 * two tabs refreshing at once, not theft. It is refused without revoking the family,
 * and the losing tab picks up the winner's token from shared storage.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    static final Duration RACE_GRACE = Duration.ofSeconds(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final UserRepository users;
    private final JwtService jwt;
    private final JwtProperties props;
    private final Clock clock;

    public SessionService(RefreshTokenRepository tokens, UserRepository users, JwtService jwt,
                          JwtProperties props, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.jwt = jwt;
        this.props = props;
        this.clock = clock;
    }

    /** A new session (new token family), on login or registration. */
    @Transactional
    public AuthResponse start(User user) {
        return issue(user, UUID.randomUUID(), null);
    }

    /**
     * Exchange a refresh token for a new pair. Runs in its own transaction so the
     * family revocation on reuse commits even though the call then fails.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public AuthResponse refresh(String raw) {
        Instant now = Instant.now(clock);
        RefreshToken current = tokens.findByHashForUpdate(hash(raw)).orElseThrow(SessionService::invalid);

        if (current.getRevokedAt() != null) {
            boolean race = current.getReplacedById() != null
                    && current.getRevokedAt().isAfter(now.minus(RACE_GRACE));
            if (!race) {
                int revoked = tokens.revokeFamily(current.getFamilyId(), now);
                log.warn("Refresh token reuse detected for user {}: revoked {} tokens in family {}",
                        current.getUserId(), revoked, current.getFamilyId());
            }
            throw invalid();
        }
        if (!now.isBefore(current.getExpiresAt())) throw invalid();

        User user = users.findById(current.getUserId()).orElseThrow(SessionService::invalid);
        return issue(user, current.getFamilyId(), current);
    }

    /** Sign out this device: revoke the token's whole family. Unknown tokens are ignored. */
    @Transactional
    public void end(String raw) {
        if (raw == null || raw.isBlank()) return;
        tokens.findByHashForUpdate(hash(raw)).ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), Instant.now(clock)));
    }

    /** Sign out everywhere, e.g. after a password change. */
    @Transactional
    public void endAll(Long userId) {
        tokens.revokeAllForUser(userId, Instant.now(clock));
    }

    @Scheduled(cron = "${app.sessions.cleanup-cron:0 30 3 * * *}")
    @Transactional
    public void purgeExpired() {
        int deleted = tokens.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(7)));
        if (deleted > 0) log.info("Purged {} expired refresh tokens", deleted);
    }

    private AuthResponse issue(User user, UUID family, RefreshToken replacing) {
        Instant now = Instant.now(clock);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        RefreshToken next = tokens.save(new RefreshToken(user.getId(), family, hash(raw), now,
                now.plus(Duration.ofDays(props.getRefreshTtlDays()))));
        if (replacing != null) replacing.revoke(now, next.getId());
        return new AuthResponse(jwt.generateToken(user), raw, props.getExpirationMs() / 1000, user.getEmail(), user.getName());
    }

    private static String hash(String raw) {
        return Text.sha256(raw == null ? "" : raw);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again.");
    }
}
