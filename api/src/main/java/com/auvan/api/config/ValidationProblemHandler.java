package com.auvan.api.config;

import jakarta.validation.ConstraintViolation;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Runs ahead of Boot's handler, which answers every violation with "Invalid request content."
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class ValidationProblemHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidRequest(MethodArgumentNotValidException invalid) {
        ProblemDetail problem = invalid.getBody();
        invalid.getFieldErrors().stream()
                .filter(ValidationProblemHandler::hasWrittenMessage)
                .findFirst()
                .ifPresent(error -> problem.setDetail(error.getDefaultMessage()));
        return problem;
    }

    // A built-in template such as "{jakarta.validation.constraints.NotNull.message}" is not a sentence for a person.
    private static boolean hasWrittenMessage(FieldError error) {
        return error.contains(ConstraintViolation.class)
                && !error.unwrap(ConstraintViolation.class).getMessageTemplate().startsWith("{");
    }
}
