package io.genfin.razorpay.mapper;

import io.genfin.money.money.Money;
import io.genfin.payment.gateway.GatewayRequest;
import io.genfin.providerapi.capability.CaptureMode;
import java.math.BigDecimal;
import java.util.Locale;
import org.json.JSONObject;

/**
 * Maps the engine's provider-neutral {@link GatewayRequest} onto Razorpay's JSON request shape. No
 * engine type leaks past this class.
 */
public final class RazorpayRequestMapper {

  private RazorpayRequestMapper() {}

  public static JSONObject toOrderCreateRequest(GatewayRequest request, CaptureMode captureMode) {
    JSONObject json = new JSONObject();
    json.put("amount", toMinorUnits(request.amount()));
    json.put("currency", request.amount().currency().code().toUpperCase(Locale.ROOT));
    json.put("payment_capture", captureMode == CaptureMode.AUTOMATIC ? 1 : 0);
    JSONObject notes = new JSONObject();
    request.metadata().values().forEach(notes::put);
    json.put("notes", notes);
    return json;
  }

  public static JSONObject toCaptureRequest(GatewayRequest request) {
    JSONObject json = new JSONObject();
    json.put("amount", toMinorUnits(request.amount()));
    json.put("currency", request.amount().currency().code().toUpperCase(Locale.ROOT));
    return json;
  }

  public static JSONObject toRefundRequest(GatewayRequest request) {
    JSONObject json = new JSONObject();
    json.put("amount", toMinorUnits(request.amount()));
    return json;
  }

  private static long toMinorUnits(Money amount) {
    BigDecimal minorUnits = amount.amount().movePointRight(amount.currency().fractionDigits());
    return minorUnits.longValueExact();
  }
}
