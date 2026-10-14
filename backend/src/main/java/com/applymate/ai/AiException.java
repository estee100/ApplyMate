package com.applymate.ai;

import com.applymate.error.ApiException;
import org.springframework.http.HttpStatus;

/** AI failures, each mapped to a status the frontend can explain honestly. */
public final class AiException {

    private AiException() {}

    /** No API key: the feature is off, not broken. */
    public static ApiException disabled() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "AI features are turned off. Set GEMINI_API_KEY on the server to enable them.");
    }

    /** Quota reached. Retryable, and worth saying so plainly rather than as a generic failure. */
    public static ApiException rateLimited(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, message);
    }

    public static ApiException busy() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "The AI service is busy right now. Please try again in a moment.");
    }

    public static ApiException failed() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI service returned an error. Please try again.");
    }

    public static ApiException unreadable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "The AI returned an unreadable response. Please try again.");
    }
}
