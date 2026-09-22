package com.shreeya.vehicletitle.application;

import java.util.UUID;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException(UUID transactionId) {
        super("Title transaction " + transactionId + " was not found");
    }
}
