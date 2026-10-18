package com.company.ppmstarter;

/**
 * Thrown at startup when a consumer has implemented <em>some but not all</em>
 * of the repository ports a ppm-core aggregate requires — a strong signal of
 * a forgotten port bean, not an intentional decision to skip the aggregate
 * entirely (skipping all of an aggregate's ports is fine and silently
 * disables that aggregate's use-case bean; see {@link PpmUseCaseAutoConfiguration}).
 *
 * <p>Deliberately distinct from Spring's own {@code NoSuchBeanDefinitionException}
 * — that exception fires later, at the point some other bean tries to inject
 * the missing use-case service, with no indication of which port was the
 * actual cause. This exception fires immediately at startup, names the
 * aggregate and the exact missing port interface(s), and states the fix.
 */
public class PpmMissingRepositoryPortException extends RuntimeException {

    public PpmMissingRepositoryPortException(String message) {
        super(message);
    }
}
