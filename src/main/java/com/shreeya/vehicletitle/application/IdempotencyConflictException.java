package com.shreeya.vehicletitle.application;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(String key) {
        super("Idempotency key '" + key + "' was already used with a different request");
    }
}
