module io.genfin.tax {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;

  exports io.genfin.tax.api.party;
  exports io.genfin.tax.api.supply;
  exports io.genfin.tax.api.transaction;
  exports io.genfin.tax.api.decision;
  exports io.genfin.tax.api.rate;
  exports io.genfin.tax.api.metadata;
  exports io.genfin.tax.api.exception;
  exports io.genfin.tax.api.config;
  exports io.genfin.tax.port;
  exports io.genfin.tax.spi;
}
