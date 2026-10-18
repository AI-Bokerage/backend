module io.genfin.reconciliation {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;
  requires transitive io.genfin.payment;
  requires transitive io.genfin.refund;

  exports io.genfin.reconciliation.id;
  exports io.genfin.reconciliation.reconciliation;
  exports io.genfin.reconciliation.lifecycle;
  exports io.genfin.reconciliation.matching;
  exports io.genfin.reconciliation.comparison;
  exports io.genfin.reconciliation.discrepancy;
  exports io.genfin.reconciliation.tolerance;
  exports io.genfin.reconciliation.rule;
  exports io.genfin.reconciliation.summary;
  exports io.genfin.reconciliation.calculation;
  exports io.genfin.reconciliation.report;
  exports io.genfin.reconciliation.config;
  exports io.genfin.reconciliation.validation;
  exports io.genfin.reconciliation.event;
  exports io.genfin.reconciliation.serialization;
  exports io.genfin.reconciliation.port.lifecycle;
  exports io.genfin.reconciliation.port.validation;
  exports io.genfin.reconciliation.port.matching;
  exports io.genfin.reconciliation.port.comparison;
  exports io.genfin.reconciliation.port.discrepancy;
  exports io.genfin.reconciliation.port.tolerance;
  exports io.genfin.reconciliation.port.rule;
  exports io.genfin.reconciliation.port.calculation;
  exports io.genfin.reconciliation.port.report;
  exports io.genfin.reconciliation.spi;
}
