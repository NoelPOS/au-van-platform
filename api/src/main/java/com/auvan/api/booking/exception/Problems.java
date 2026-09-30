package com.auvan.api.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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

    // The cause is for the log only; never render it: SDK messages name the bucket and endpoint.
    public static ResponseStatusException serviceUnavailable(String code, String detail, Throwable cause) {
        return of(HttpStatus.SERVICE_UNAVAILABLE, code, detail, cause);
    }

    private static ResponseStatusException of(HttpStatus status, String code, String detail, Throwable cause) {
        var exception = new ResponseStatusException(status, detail, cause);
        exception.getBody().setProperty("code", code);
        return exception;
    }
}
