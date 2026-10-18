package com.company.ppmsvc.promocode.port;

import com.company.ppmsvc.promocode.model.PromoCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain port for {@link PromoCode} persistence.
 *
 * <p>Application services depend only on this interface; the concrete
 * implementation is supplied by the host application (see INTEGRATION_GUIDE.md in ppm-core).
 *
 * <p>No JPA types cross this interface.
 */
public interface PromoCodeRepositoryPort {

    /**
     * Persists a new or updated {@link PromoCode} and returns the saved state.
     *
     * <p>Pass {@code version = null} for a new entity so that Spring Data
     * calls {@code persist()} rather than {@code merge()}.
     */
    PromoCode save(PromoCode promoCode);

    /** Returns the promo code with the given ID, or empty if not found (or soft-deleted). */
    Optional<PromoCode> findById(UUID id);

    /** Returns all non-deleted promo code rows ordered by {@code code} ascending. */
    List<PromoCode> findAll();

    /** Returns the non-deleted promo code with the given code string, or empty if not found. */
    Optional<PromoCode> findByCode(String code);

    /** Returns {@code true} if a non-deleted promo code with the given code string already exists. */
    boolean existsByCode(String code);

    /**
     * Soft-deletes the promo code with the given ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code PROMO_CODE_NOT_FOUND} if no active row exists for the given ID.
     */
    void softDelete(UUID id, UUID actorId);
}
