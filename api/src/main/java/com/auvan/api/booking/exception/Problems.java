package com.auvan.api.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds the RFC 7807 responses this module returns, each carrying a stable
 * machine-readable {@code code} alongside the human {@code detail}.
 *
 * <p>The code exists because the status alone is not enough to act on: booking
 * creation answers {@code 409} for seven different conditions, and the web
 * client has to tell "your hold expired, pick again" from "you already booked
 * this hold, go and look at it". The client branches on {@code code}; the
 * {@code detail} is prose and may be reworded at any time.
 */
public final class Problems {
    private Problems() { }

    public static ResponseStatusException badRequest(String code, String detail) {
        return of(HttpStatus.BAD_REQUEST, code, detail, null);
    }

    public static ResponseStatusException notFound(String code, String detail) {
        return of(HttpStatus.NOT_FOUND, code, detail, null);
    }

    public static ResponseStatusException conflict(String code, String detail) {
        return of(HttpStatus.CONFLICT, code, detail, null);
    }

    public static ResponseStatusException conflict(String code, String detail, Throwable cause) {
        return of(HttpStatus.CONFLICT, code, detail, cause);
    }

    /**
     * A dependency this module needs was not reachable. The cause is attached
     * for the log and never rendered: the body carries only the code and the
     * prose, so a storage failure cannot answer a student with a stack trace.
     */
    public static ResponseStatusException serviceUnavailable(String code, String detail, Throwable cause) {
        return of(HttpStatus.SERVICE_UNAVAILABLE, code, detail, cause);
    }

    private static ResponseStatusException of(HttpStatus status, String code, String detail, Throwable cause) {
        var exception = new ResponseStatusException(status, detail, cause);
        // getBody() hands back the very ProblemDetail that will be serialised,
        // so the extra member survives all the way to the response.
        exception.getBody().setProperty("code", code);
        return exception;
    }
}
