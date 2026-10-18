module io.genfin.dunning {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;
  requires transitive io.genfin.payment;

  exports io.genfin.dunning.id;
  exports io.genfin.dunning.reference;
  exports io.genfin.dunning.obligation;
  exports io.genfin.dunning.collection;
  exports io.genfin.dunning.dunning;
  exports io.genfin.dunning.event;
  exports io.genfin.dunning.lifecycle;
  exports io.genfin.dunning.port.lifecycle;
  exports io.genfin.dunning.calendar;
  exports io.genfin.dunning.port.calendar;
  exports io.genfin.dunning.backoff;
  exports io.genfin.dunning.port.backoff;
  exports io.genfin.dunning.retry;
  exports io.genfin.dunning.port.retry;
  exports io.genfin.dunning.schedule;
  exports io.genfin.dunning.port.schedule;
  exports io.genfin.dunning.reminder;
  exports io.genfin.dunning.port.reminder;
  exports io.genfin.dunning.notification;
  exports io.genfin.dunning.port.notification;
  exports io.genfin.dunning.escalation;
  exports io.genfin.dunning.port.escalation;
  exports io.genfin.dunning.policy;
  exports io.genfin.dunning.port.policy;
  exports io.genfin.dunning.rule;
  exports io.genfin.dunning.port.rule;
  exports io.genfin.dunning.failure;
  exports io.genfin.dunning.port.failure;
  exports io.genfin.dunning.validation;
  exports io.genfin.dunning.port.validation;
  exports io.genfin.dunning.config;
  exports io.genfin.dunning.spi;
  exports io.genfin.dunning.serialization;
}
