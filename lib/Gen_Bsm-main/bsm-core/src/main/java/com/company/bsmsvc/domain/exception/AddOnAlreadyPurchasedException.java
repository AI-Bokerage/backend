package com.company.bsmsvc.domain.exception;

public class AddOnAlreadyPurchasedException extends RuntimeException {

    public AddOnAlreadyPurchasedException(String message) {
        super(message);
    }
}
