package com.applymate.controller;

import com.applymate.dto.AuthResponse;
import com.applymate.dto.LoginRequest;
import com.applymate.dto.RegisterRequest;
import com.applymate.service.AccountService;
import com.applymate.service.AuthService;
import com.applymate.service.RateLimiter;
import com.applymate.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String TOO_MANY = "Too many attempts. Wait a few minutes and try again.";

    private final AuthService authService;
    private final AccountService accountService;
    private final RateLimiter limiter;
    private final SessionService sessionService;
    private final int registerPerHourPerIp;
    private final int loginPerWindowPerIp;

    public AuthController(AuthService authService, AccountService accountService, RateLimiter limiter,
                          SessionService sessionService,
                          @Value("${app.auth.register-per-hour-per-ip:10}") int registerPerHourPerIp,
                          @Value("${app.auth.login-per-15-minutes-per-ip:50}") int loginPerWindowPerIp) {
        this.authService = authService;
        this.accountService = accountService;
        this.limiter = limiter;
        this.sessionService = sessionService;
        this.registerPerHourPerIp = registerPerHourPerIp;
        this.loginPerWindowPerIp = loginPerWindowPerIp;
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {}

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank @Size(min = 8, max = 100) String password) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 100) String newPassword) {}

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        limiter.check("register:" + http.getRemoteAddr(), registerPerHourPerIp, Duration.ofHours(1), TOO_MANY);
        return authService.register(request);
    }

    /**
     * Limited per email (guessing one account's password) and per address (spraying
     * one password across many accounts).
     */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        String emailKey = "login:" + AuthService.normalizeEmail(request.email());
        limiter.check(emailKey, 10, WINDOW, TOO_MANY);
        limiter.check("login-ip:" + http.getRemoteAddr(), loginPerWindowPerIp, WINDOW, TOO_MANY);
        AuthResponse response = authService.login(request);
        limiter.reset(emailKey);
        return response;
    }

    /** Trade a refresh token for a new access token and a new refresh token. */
    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        limiter.check("refresh-ip:" + http.getRemoteAddr(), 120, WINDOW, TOO_MANY);
        return sessionService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshRequest request) {
        sessionService.end(request == null ? null : request.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        limiter.check("forgot:" + AuthService.normalizeEmail(request.email()), 3, WINDOW, TOO_MANY);
        limiter.check("forgot-ip:" + http.getRemoteAddr(), 20, WINDOW, TOO_MANY);
        accountService.requestReset(request.email());
        // Identical whether or not the account exists.
        return Map.of("message", "If an account exists for that email, a reset link is on its way.");
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletRequest http) {
        limiter.check("reset-ip:" + http.getRemoteAddr(), 20, WINDOW, TOO_MANY);
        accountService.reset(request.token(), request.password());
        return Map.of("message", "Your password has been changed. Sign in with your new password.");
    }

    @PostMapping("/change-password")
    public AuthResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return accountService.changePassword(request.currentPassword(), request.newPassword());
    }
}
