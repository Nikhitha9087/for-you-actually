package com.foryouactually.backend.error;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a request carries a userId the database has never seen (e.g. a browser
 * holding a stale id from an earlier deployment after the catalogue DB was rebuilt).
 * Mapped to 404 so the frontend can self-heal: clear the id and send the visitor back
 * through onboarding, rather than surfacing a generic "backend down" error.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class UnknownUserException extends RuntimeException {
    public UnknownUserException(String userId) {
        super("Unknown user: " + userId);
    }
}
