package com.company.ppmsvc.addon.usecase;

import com.company.ppmsvc.addon.model.AddOn;
import com.company.ppmsvc.addon.model.AddOnType;
import java.util.List;
import java.util.UUID;

/**
 * Primary port (use-case boundary) for the Add-On Catalog feature.
 *
 * <p>Framework-agnostic — operates exclusively on domain models and
 * primitives. A host application resolves the acting user and translates
 * its own request/response contracts to and from these signatures.
 */
public interface AddOnApplicationService {

    /** Creates and returns a new add-on (BR-A1 through BR-A4). */
    AddOn createAddOn(UUID actorId, String code, String name, String description, AddOnType type, Boolean active);

    /**
     * Applies a partial update to an existing add-on and returns the updated state.
     * {@code null} arguments are ignored; existing values are preserved.
     * Code and type are immutable after creation (BR-A5, BR-A6).
     */
    AddOn updateAddOn(UUID actorId, UUID id, String name, String description, Boolean active);

    /** Returns the add-on with the given ID, or throws {@code ADD_ON_NOT_FOUND}. */
    AddOn getAddOn(UUID id);

    /** Returns the add-on with the given code, or throws {@code ADD_ON_NOT_FOUND}. */
    AddOn getAddOnByCode(String code);

    /**
     * Lists all add-ons matching the supplied filters, ordered by code ascending.
     * A {@code null} filter value means "no filter on this dimension".
     */
    List<AddOn> listAddOns(Boolean active, AddOnType type);

    /**
     * Soft-deletes the add-on with the given ID (BR-A8).
     * Throws {@code ADD_ON_NOT_FOUND} if no active add-on with that ID exists.
     */
    void deleteAddOn(UUID actorId, UUID id);
}
