package com.company.ppmsvc.coupon.port;

import com.company.ppmsvc.coupon.model.Coupon;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link Coupon} persistence. No JPA types cross this interface.
 */
public interface CouponRepositoryPort {

    /** Persists a new or updated {@link Coupon} and returns the saved state. */
    Coupon save(Coupon coupon);

    /** Returns the coupon with the given ID, or empty if not found (or soft-deleted). */
    Optional<Coupon> findById(UUID id);

    /** Returns all non-deleted coupon rows. */
    List<Coupon> findAll();

    /** Returns the non-deleted coupon with the given code, or empty if not found. */
    Optional<Coupon> findByCode(String code);

    /** Returns {@code true} if a non-deleted coupon with the given code already exists. */
    boolean existsByCode(String code);

    /**
     * Soft-deletes the coupon with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code COUPON_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
