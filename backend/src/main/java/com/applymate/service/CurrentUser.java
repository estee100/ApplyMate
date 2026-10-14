package com.applymate.service;

import com.applymate.model.User;
import com.applymate.repository.UserRepository;
import org.springframework.http.HttpStatus;
import com.applymate.error.ApiException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the signed-in user from the JWT-populated security context. */
@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in");
        }
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Not signed in"));
    }

    public Long id() {
        return get().getId();
    }
}
