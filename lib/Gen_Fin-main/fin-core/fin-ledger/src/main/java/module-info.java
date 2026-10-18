module io.genfin.ledger {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;
  requires transitive io.genfin.payment;
  requires transitive io.genfin.refund;
  requires transitive io.genfin.reconciliation;

  exports io.genfin.ledger.id;
  exports io.genfin.ledger.ledger;
  exports io.genfin.ledger.fact;
  exports io.genfin.ledger.journal;
  exports io.genfin.ledger.lifecycle;
  exports io.genfin.ledger.account;
  exports io.genfin.ledger.posting;
  exports io.genfin.ledger.balance;
  exports io.genfin.ledger.period;
  exports io.genfin.ledger.reversal;
  exports io.genfin.ledger.validation;
  exports io.genfin.ledger.trialbalance;
  exports io.genfin.ledger.report;
  exports io.genfin.ledger.calculation;
  exports io.genfin.ledger.config;
  exports io.genfin.ledger.event;
  exports io.genfin.ledger.port.lifecycle;
  exports io.genfin.ledger.port.account;
  exports io.genfin.ledger.port.journal;
  exports io.genfin.ledger.port.posting;
  exports io.genfin.ledger.port.balance;
  exports io.genfin.ledger.port.period;
  exports io.genfin.ledger.port.reversal;
  exports io.genfin.ledger.port.validation;
  exports io.genfin.ledger.port.trialbalance;
  exports io.genfin.ledger.port.report;
  exports io.genfin.ledger.port.calculation;
  exports io.genfin.ledger.spi;
  exports io.genfin.ledger.serialization;
}
