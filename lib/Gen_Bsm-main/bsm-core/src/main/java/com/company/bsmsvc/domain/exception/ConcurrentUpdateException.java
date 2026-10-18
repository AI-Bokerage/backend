package com.company.bsmsvc.domain.exception;

/**
 * Raised when a repository port's save fails because another writer already changed the same
 * record (optimistic concurrency conflict). Callers typically treat this as expected under
 * concurrent scheduler execution — the other writer won, this one should back off rather than
 * treat it as a hard failure. Repository adapters translate their persistence framework's
 * concurrency-conflict exception into this type; application services never see the underlying
 * framework exception.
 */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
