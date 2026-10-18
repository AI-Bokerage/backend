module io.genfin.payment {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;

  exports io.genfin.payment.exception;
  exports io.genfin.payment.reference;
  exports io.genfin.payment.failure;
  exports io.genfin.payment.lifecycle;
  exports io.genfin.payment.method;
  exports io.genfin.payment.idempotency;
  exports io.genfin.payment.id;
  exports io.genfin.payment.payment;
  exports io.genfin.payment.intent;
  exports io.genfin.payment.session;
  exports io.genfin.payment.attempt;
  exports io.genfin.payment.authorization;
  exports io.genfin.payment.event;
  exports io.genfin.payment.gateway;
  exports io.genfin.payment.calculation;
  exports io.genfin.payment.validation;
  exports io.genfin.payment.config;
  exports io.genfin.payment.serialization;
  exports io.genfin.payment.spi;
  exports io.genfin.payment.metadata;
  exports io.genfin.payment.port.failure;
  exports io.genfin.payment.port.lifecycle;
  exports io.genfin.payment.port.method;
  exports io.genfin.payment.port.idempotency;
  exports io.genfin.payment.port.gateway;
  exports io.genfin.payment.port.calculation;
  exports io.genfin.payment.port.validation;
  exports io.genfin.payment.port.authorization;
}
