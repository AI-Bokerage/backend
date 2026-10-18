package io.genfin.reconciliation.port.discrepancy;

import io.genfin.api.exception.ValidationException;
import io.genfin.reconciliation.discrepancy.DiscrepancyReason;
import io.genfin.reconciliation.discrepancy.DiscrepancyReasonDescriptor;
import java.util.List;
import java.util.Optional;

public interface DiscrepancyReasonRegistry {

  void register(DiscrepancyReasonDescriptor descriptor);

  Optional<DiscrepancyReasonDescriptor> find(DiscrepancyReason reason);

  default DiscrepancyReasonDescriptor require(DiscrepancyReason reason) {
    return find(reason)
        .orElseThrow(
            () -> new ValidationException("Unsupported discrepancy reason: " + reason.code()));
  }

  List<DiscrepancyReasonDescriptor> findAll();
}
