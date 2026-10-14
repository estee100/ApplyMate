package com.applymate.service;

import com.applymate.config.AppProperties;
import com.applymate.dto.AuthResponse;
import com.applymate.error.ApiException;
import com.applymate.model.PasswordResetToken;
import com.applymate.model.User;
import com.applymate.repository.PasswordResetTokenRepository;
import com.applymate.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import com.applymate.util.Text;

/** Password reset, password change and account deletion. */
@Service
public class AccountService {

    static final Duration RESET_TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SessionService sessions;
    private final MailService mail;
    private final CurrentUser currentUser;
    private final AppProperties properties;
    private final Clock clock;

    public AccountService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                          SessionService sessions, MailService mail, CurrentUser currentUser, AppProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.sessions = sessions;
        this.mail = mail;
        this.currentUser = currentUser;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Always succeeds from the caller's point of view, so it cannot be used to find out
     * which emails have accounts. The email is sent off the request thread.
     */
    @Transactional
    public void requestReset(String rawEmail) {
        users.findByEmail(AuthService.normalizeEmail(rawEmail)).ifPresent(user -> {
            Instant now = Instant.now(clock);
            tokens.retireAll(user.getId(), now);
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            // Only the hash is stored: a database leak cannot be turned into working links.
            tokens.save(new PasswordResetToken(user.getId(), Text.sha256(token), now.plus(RESET_TTL)));
            String link = properties.frontendUrl() + "/reset-password?token=" + token;
            mail.send(user.getEmail(), "Reset your ApplyMate password",
                    "Someone asked to reset the password for your ApplyMate account.\n\n"
                            + "Use this link within 30 minutes to choose a new one:\n" + link + "\n\n"
                            + "If it wasn't you, ignore this email; your password has not changed.",
                    link);
        });
    }

    /** Sets the new password and signs out every existing session. */
    @Transactional
    public void reset(String token, String newPassword) {
        Instant now = Instant.now(clock);
        PasswordResetToken row = tokens.findByTokenHash(Text.sha256(token == null ? "" : token))
                .filter(t -> t.isUsable(now))
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired. Request a new one."));
        User user = users.findById(row.getUserId()).orElseThrow(() -> ApiException.badRequest("This reset link is invalid."));
        user.setPasswordHash(encoder.encode(newPassword));
        user.bumpTokenVersion();
        users.save(user);
        sessions.endAll(user.getId());
        row.markUsed(now);
        tokens.save(row);
        tokens.retireAll(user.getId(), now);
    }

    /** Changes the password, revokes other sessions, and returns a fresh token for this one. */
    @Transactional
    public AuthResponse changePassword(String current, String next) {
        User user = currentUser.get();
        if (!encoder.matches(current, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your current password is incorrect.");
        }
        user.setPasswordHash(encoder.encode(next));
        user.bumpTokenVersion();
        users.save(user);
        sessions.endAll(user.getId());
        return sessions.start(user);
    }

    /** Deletes the account and, by cascade, every resume, match, document and application. */
    @Transactional
    public void deleteAccount(String password) {
        User user = currentUser.get();
        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Your password is incorrect.");
        }
        users.delete(user);
    }
}
