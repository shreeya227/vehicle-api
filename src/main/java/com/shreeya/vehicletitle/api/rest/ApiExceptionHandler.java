package com.shreeya.vehicletitle.api.rest;

import com.shreeya.vehicletitle.application.IdempotencyConflictException;
import com.shreeya.vehicletitle.application.InvalidSubmissionException;
import com.shreeya.vehicletitle.application.TransactionNotFoundException;
import com.shreeya.vehicletitle.domain.InvalidTransitionException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail notFound(TransactionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Transaction not found", exception.getMessage());
    }

    @ExceptionHandler({IdempotencyConflictException.class, InvalidTransitionException.class,
            DataIntegrityViolationException.class})
    ProblemDetail conflict(RuntimeException exception) {
        String detail = exception instanceof DataIntegrityViolationException
                ? "A conflicting transaction was committed concurrently; retry with the same idempotency key"
                : exception.getMessage();
        return problem(HttpStatus.CONFLICT, "Transaction conflict", detail);
    }

    @ExceptionHandler({InvalidSubmissionException.class, IllegalArgumentException.class})
    ProblemDetail invalidRequest(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request validation failed");
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", detail);
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://github.com/shreeya227/vehicle-title-transaction-api/problems/"
                + status.value()));
        return problem;
    }
}
