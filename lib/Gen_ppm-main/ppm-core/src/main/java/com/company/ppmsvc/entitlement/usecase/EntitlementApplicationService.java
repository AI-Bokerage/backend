package com.company.ppmsvc.entitlement.usecase;

import com.company.ppmsvc.entitlement.model.Entitlement;
import com.company.ppmsvc.entitlement.model.EntitlementType;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Entitlement Catalog.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface EntitlementApplicationService {

    /**
     * Creates a new entitlement catalog entry.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-E1: {@code code} must be unique across all active (non-deleted) entitlements.</li>
     *   <li>BR-E3: {@code code} is trimmed before persistence.</li>
     *   <li>BR-E4: {@code name} is trimmed before persistence.</li>
     *   <li>BR-E5: {@code active} defaults to {@code true} when {@code null}.</li>
     *   <li>BR-E7: {@code createdBy} / {@code updatedBy} are set to {@code actorId}.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code ENTITLEMENT_CODE_ALREADY_EXISTS} if the code is already in use.
     */
    Entitlement createEntitlement(UUID actorId, String code, String name, String description,
                                   EntitlementType type, Boolean active);

    /**
     * Partially updates an existing entitlement catalog entry.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-E2: {@code code} is immutable — never changed via update.</li>
     *   <li>BR-E4: If {@code name} is supplied it is trimmed; a blank value is rejected.</li>
     *   <li>BR-E6: Soft-deleted entitlements are invisible and throw not-found.</li>
     *   <li>BR-E7: {@code updatedAt} and {@code updatedBy} are refreshed.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ENTITLEMENT_NOT_FOUND} if the entitlement does not exist or is soft-deleted.
     */
    Entitlement updateEntitlement(UUID actorId, UUID entitlementId, String name, String description,
                                   Boolean active);

    /**
     * Returns a single entitlement by its ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ENTITLEMENT_NOT_FOUND} if the entitlement does not exist or is soft-deleted.
     */
    Entitlement getEntitlement(UUID entitlementId);

    /**
     * Returns a single entitlement by its stable code.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ENTITLEMENT_NOT_FOUND} if no active entitlement has the given code.
     */
    Entitlement getEntitlementByCode(String code);

    /**
     * Returns all non-deleted entitlements matching the supplied filters,
     * ordered by code ascending. A {@code null} filter value means "no filter".
     */
    List<Entitlement> listEntitlements(Boolean active, EntitlementType type);

    /**
     * Soft-deletes an existing entitlement.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code ENTITLEMENT_NOT_FOUND} if the entitlement does not exist or is already deleted.
     */
    void deleteEntitlement(UUID actorId, UUID entitlementId);
}
