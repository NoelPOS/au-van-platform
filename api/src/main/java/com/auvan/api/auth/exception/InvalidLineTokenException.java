package com.auvan.api.auth.exception;

public class InvalidLineTokenException extends RuntimeException {
    public InvalidLineTokenException() {
        super("The LINE identity token could not be verified.");
    }
}
