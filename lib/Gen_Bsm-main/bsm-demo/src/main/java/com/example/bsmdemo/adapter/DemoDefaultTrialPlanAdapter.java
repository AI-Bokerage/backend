package com.example.bsmdemo.adapter;

import com.company.bsmsvc.domain.model.PpmDefaultTrialResult;
import com.company.bsmsvc.domain.port.DefaultTrialPlanPort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Hardcoded "Free Trial" plan — stands in for a real PPM-SVC lookup. */
@Component
public class DemoDefaultTrialPlanAdapter implements DefaultTrialPlanPort {

    private static final UUID FREE_TRIAL_PLAN_ID = UUID.nameUUIDFromBytes("demo-free-trial-plan".getBytes());
    private static final UUID FREE_TRIAL_PLAN_VERSION_ID = UUID.nameUUIDFromBytes("demo-free-trial-plan-version".getBytes());

    @Override
    public PpmDefaultTrialResult getDefaultTrialPlan() {
        return new PpmDefaultTrialResult(FREE_TRIAL_PLAN_ID, "FREE_TRIAL", FREE_TRIAL_PLAN_VERSION_ID);
    }
}
