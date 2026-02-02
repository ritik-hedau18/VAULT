package com.vault.exception;

public class InvalidTransactionPinException extends RuntimeException {
    public InvalidTransactionPinException(String message) {
        super(message);
    }
}
