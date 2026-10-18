package com.company.ppmdemo.web;

import com.company.ppmsvc.module.model.Module;
import com.company.ppmsvc.module.usecase.ModuleApplicationService;
import com.company.ppmsvc.plan.model.Plan;
import com.company.ppmsvc.plan.model.PlanVisibility;
import com.company.ppmsvc.plan.usecase.PlanApplicationService;
import com.company.ppmsvc.planmodule.usecase.PlanModuleApplicationService;
import com.company.ppmsvc.promocode.model.PromoCode;
import com.company.ppmsvc.promocode.usecase.PromoCodeApplicationService;
import com.company.ppmsvc.promocode.model.PromoValidationResult;
import com.company.ppmsvc.promocode.usecase.PromoValidationService;
import com.company.ppmdemo.web.dto.CreateModuleRequest;
import com.company.ppmdemo.web.dto.CreatePlanRequest;
import com.company.ppmdemo.web.dto.CreatePromoCodeRequest;
import com.company.ppmdemo.web.dto.ModuleView;
import com.company.ppmdemo.web.dto.PlanView;
import com.company.ppmdemo.web.dto.PromoCodeView;
import com.company.ppmdemo.web.dto.PromoValidationView;
import com.company.ppmdemo.web.dto.ValidatePromoRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exercises a representative slice of ppm-core's public API: create a plan,
 * list plans, create a module, assign it to a plan, create a promo code, and
 * validate it.
 *
 * <p>This controller is the only place in ppm-demo that touches ppm-core's
 * use-case interfaces directly — exactly the shape INTEGRATION_GUIDE.md
 * describes for a real host: it resolves an actor id (hardcoded here, since
 * ppm-demo has no security stack) and maps between its own DTOs and
 * ppm-core's domain models. No ppm-core type is serialized directly.
 */
@RestController
@RequiredArgsConstructor
public class DemoController {

    /** Demo has no auth — a fixed actor id stands in for SecurityUtils.requireCurrentUserId(). */
    private static final UUID DEMO_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d0");

    private final PlanApplicationService       planService;
    private final ModuleApplicationService     moduleService;
    private final PlanModuleApplicationService planModuleService;
    private final PromoCodeApplicationService  promoCodeService;
    private final PromoValidationService       promoValidationService;

    // ── Plans ─────────────────────────────────────────────────────────────────

    @PostMapping("/demo/plans")
    public ResponseEntity<PlanView> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        Plan saved = planService.createPlan(DEMO_ACTOR_ID, request.code(), request.name(),
            null, null, PlanVisibility.PUBLIC, request.trialDays(), true, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @GetMapping("/demo/plans")
    public List<PlanView> listPlans() {
        return planService.listPlans(null, null).stream().map(this::toView).toList();
    }

    // ── Modules ───────────────────────────────────────────────────────────────

    @PostMapping("/demo/modules")
    public ResponseEntity<ModuleView> createModule(@Valid @RequestBody CreateModuleRequest request) {
        Module saved = moduleService.createModule(DEMO_ACTOR_ID, request.code(),
            request.name(), request.description(), true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @PostMapping("/demo/plans/{planId}/modules/{moduleId}")
    public List<ModuleView> assignModule(@PathVariable UUID planId, @PathVariable UUID moduleId) {
        return planModuleService.assignModules(DEMO_ACTOR_ID, planId, Set.of(moduleId))
            .stream().map(this::toView).toList();
    }

    // ── Promo codes ───────────────────────────────────────────────────────────

    @PostMapping("/demo/promo-codes")
    public ResponseEntity<PromoCodeView> createPromoCode(@Valid @RequestBody CreatePromoCodeRequest request) {
        PromoCode saved = promoCodeService.createPromoCode(DEMO_ACTOR_ID, request.code(),
            request.discountType(), request.value(), request.validFrom(), request.validUntil(),
            null, false, true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toView(saved));
    }

    @PostMapping("/demo/promo-codes/validate")
    public PromoValidationView validatePromo(@Valid @RequestBody ValidatePromoRequest request) {
        PromoValidationResult result = promoValidationService.validate(request.code(), request.planId());
        return new PromoValidationView(result.valid(), result.reason().getValue());
    }

    // ── mapping ───────────────────────────────────────────────────────────────

    private PlanView toView(Plan p) {
        return new PlanView(p.getId(), p.getCode(), p.getSlug(), p.getName(), p.isActive(), p.getTrialDays());
    }

    private ModuleView toView(Module m) {
        return new ModuleView(m.getId(), m.getCode(), m.getName(), m.isActive());
    }

    private PromoCodeView toView(PromoCode pc) {
        return new PromoCodeView(pc.getId(), pc.getCode(), Boolean.TRUE.equals(pc.getActive()));
    }
}
