package io.genfin.reconciliation.port.discrepancy;

import io.genfin.api.port.spi.Extension;
import io.genfin.reconciliation.discrepancy.DiscrepancyReasonDescriptor;
import java.util.List;

public interface DiscrepancyReasonProvider extends Extension {

  List<DiscrepancyReasonDescriptor> provide();
}
