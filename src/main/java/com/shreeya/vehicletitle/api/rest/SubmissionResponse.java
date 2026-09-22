package com.shreeya.vehicletitle.api.rest;

public record SubmissionResponse(TransactionResponse transaction, boolean idempotentReplay) {
}
