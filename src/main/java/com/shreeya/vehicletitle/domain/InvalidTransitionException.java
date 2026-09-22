package com.shreeya.vehicletitle.domain;

public class InvalidTransitionException extends RuntimeException {

    public InvalidTransitionException(TransactionStatus current, TransactionStatus requested) {
        super("Cannot transition a title transaction from " + current + " to " + requested);
    }
}
