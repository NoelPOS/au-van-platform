package com.auvan.api.auth.controller;

import com.auvan.api.auth.exception.InvalidLineTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AuthenticationExceptionHandler {
    @ExceptionHandler(InvalidLineTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse invalidLineToken() {
        return new ErrorResponse("line_token_invalid");
    }

    record ErrorResponse(String code) { }
}
