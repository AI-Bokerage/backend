package io.genfin.dunning.port.failure;

import io.genfin.api.port.spi.Extension;
import io.genfin.dunning.failure.FailureClassificationRule;
import java.util.List;

/**
 * The full, explicit policy governing how the Failure Classification engine maps a {@code
 * FailureReason}'s category onto a disposition: the classification scheme an application resolves
 * from its own {@code DunningPolicy}. fin-dunning never hardcodes a category-to-disposition mapping
 * anywhere in default/example code paths.
 */
public interface FailurePolicy extends Extension {

  List<FailureClassificationRule> classificationRules();
}
