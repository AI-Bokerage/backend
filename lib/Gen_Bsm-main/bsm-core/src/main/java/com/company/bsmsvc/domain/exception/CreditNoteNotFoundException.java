package com.company.bsmsvc.domain.exception;

public class CreditNoteNotFoundException extends RuntimeException {

    public CreditNoteNotFoundException(String message) {
        super(message);
    }
}
