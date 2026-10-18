module io.genfin.providerapi {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;
  requires transitive io.genfin.payment;

  exports io.genfin.providerapi.descriptor;
  exports io.genfin.providerapi.registry;
  exports io.genfin.providerapi.capability;
  exports io.genfin.providerapi.auth;
  exports io.genfin.providerapi.webhook;
  exports io.genfin.providerapi.retry;
  exports io.genfin.providerapi.health;
  exports io.genfin.providerapi.circuit;
  exports io.genfin.providerapi.ratelimit;
  exports io.genfin.providerapi.tokenization;
  exports io.genfin.providerapi.paymentlink;
  exports io.genfin.providerapi.checkout;
  exports io.genfin.providerapi.event;
  exports io.genfin.providerapi.config;
  exports io.genfin.providerapi.spi;
  exports io.genfin.providerapi.exception;
  exports io.genfin.providerapi.port.registry;
  exports io.genfin.providerapi.port.auth;
  exports io.genfin.providerapi.port.webhook;
  exports io.genfin.providerapi.port.retry;
  exports io.genfin.providerapi.port.health;
  exports io.genfin.providerapi.port.circuit;
  exports io.genfin.providerapi.port.ratelimit;
  exports io.genfin.providerapi.port.idempotency;
  exports io.genfin.providerapi.port.tokenization;
  exports io.genfin.providerapi.port.paymentlink;
  exports io.genfin.providerapi.port.checkout;
  exports io.genfin.providerapi.port.http;
}
