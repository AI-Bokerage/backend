package io.genfin.refund.port.reason;

import io.genfin.api.port.spi.Extension;
import io.genfin.refund.reason.RefundReasonDescriptor;
import java.util.List;

public interface RefundReasonProvider extends Extension {

  List<RefundReasonDescriptor> provide();
}
