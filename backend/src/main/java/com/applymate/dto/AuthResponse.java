package com.applymate.dto;

/**
 * @param token        short-lived access token (JWT) for the Authorization header
 * @param refreshToken opaque single-use token for POST /auth/refresh
 * @param expiresIn    access token lifetime in seconds
 */
public record AuthResponse(String token, String refreshToken, long expiresIn, String email, String name) {}
