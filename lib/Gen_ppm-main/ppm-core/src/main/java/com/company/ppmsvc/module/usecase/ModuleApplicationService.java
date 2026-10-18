package com.company.ppmsvc.module.usecase;

import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.model.ModuleCode;
import java.util.List;
import java.util.UUID;

/**
 * Application service for the Module Catalog.
 *
 * <p>Framework-agnostic business use-case boundary — operates exclusively on
 * domain models and primitives. No REST, DTO, or transport-specific types
 * cross this interface; a host application (e.g. {@code ppm-svc}) is
 * responsible for translating its own request/response contracts to and
 * from these signatures.
 */
public interface ModuleApplicationService {

    /**
     * Creates a new platform capability module.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: {@code code} must be unique across all active modules.</li>
     *   <li>BR-2: {@code active} defaults to {@code true} when {@code null}.</li>
     *   <li>BR-3: {@code createdBy} / {@code updatedBy} are set to {@code actorId}.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.BusinessException with
     *         {@code MODULE_CODE_ALREADY_EXISTS} if the code is already in use.
     */
    Module createModule(UUID actorId, ModuleCode code, String name, String description, Boolean active);

    /**
     * Partially updates an existing module.
     *
     * <p>Business rules:
     * <ul>
     *   <li>BR-1: Module must exist and must not be soft-deleted.</li>
     *   <li>BR-2: {@code name}, {@code description}, and {@code active} are optional;
     *             a {@code null} value means "leave unchanged".</li>
     *   <li>BR-3: If {@code name} is supplied it must not be blank.</li>
     *   <li>BR-4: {@code code} is immutable — preserved regardless of input.</li>
     *   <li>BR-5: {@code updatedAt} and {@code updatedBy} are refreshed.</li>
     * </ul>
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code MODULE_NOT_FOUND} if the module does not exist or is soft-deleted.
     */
    Module updateModule(UUID actorId, UUID moduleId, String name, String description, Boolean active);

    /**
     * Returns a single module by its ID.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code MODULE_NOT_FOUND} if the module does not exist or is soft-deleted.
     */
    Module getModule(UUID moduleId);

    /**
     * Returns all non-deleted modules matching the supplied filter, ordered by
     * code ascending. A {@code null} filter value means "no filter".
     */
    List<Module> listModules(Boolean active);

    /**
     * Returns a single module by its stable wire code (e.g. {@code "reporting"}).
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code MODULE_NOT_FOUND} if no active module has the given code.
     */
    Module getModuleByCode(ModuleCode code);

    /**
     * Soft-deletes an existing module.
     *
     * @throws com.company.ppmsvc.exception.ResourceNotFoundException with
     *         {@code MODULE_NOT_FOUND} if the module does not exist or is already soft-deleted.
     */
    void deleteModule(UUID actorId, UUID moduleId);
}
