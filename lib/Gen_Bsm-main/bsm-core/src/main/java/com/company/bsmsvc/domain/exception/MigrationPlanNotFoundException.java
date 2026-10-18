package com.company.bsmsvc.domain.exception;

public class MigrationPlanNotFoundException extends RuntimeException {

    public MigrationPlanNotFoundException(String message) {
        super(message);
    }
}
