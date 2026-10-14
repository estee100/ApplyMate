package com.applymate.error;

import org.springframework.http.HttpStatus;

/**
 * Optimistic-lock checks for edits. A client sends back the version it loaded; if
 * the row has moved on since, the edit would overwrite someone else's change.
 */
public final class Versions {

    private Versions() {}

    /** Throws 409 when the client sent a version and it is not the current one. */
    public static void check(Long expected, long current) {
        if (expected != null && expected != current) throw conflict();
    }

    public static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT,
                "This was changed somewhere else, maybe in another tab. Reload to see the latest version.");
    }
}
