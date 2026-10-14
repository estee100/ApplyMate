package com.applymate.service;

import com.applymate.config.JwtService;
import com.applymate.dto.AuthResponse;
import com.applymate.dto.LoginRequest;
import com.applymate.dto.RegisterRequest;
import com.applymate.error.ApiException;
import com.applymate.model.User;
import com.applymate.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuthenticationManager authenticationManager
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Normalise before the existence check, otherwise "Bob@x.com" passes the check
        // and then collides with "bob@x.com" on the unique index.
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("That email is already registered. Try signing in.");
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }
        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), user.getEmail(), user.getName());
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> ApiException.notFound("User"));
        return new AuthResponse(jwtService.generateToken(user), user.getEmail(), user.getName());
    }
}
