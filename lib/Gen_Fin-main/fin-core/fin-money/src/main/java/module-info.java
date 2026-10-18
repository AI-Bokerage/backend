module io.genfin.money {
  requires transitive io.genfin.api;

  exports io.genfin.money.exception;
  exports io.genfin.money.currency;
  exports io.genfin.money.money;
  exports io.genfin.money.arithmetic;
  exports io.genfin.money.precision;
  exports io.genfin.money.rounding;
  exports io.genfin.money.conversion;
  exports io.genfin.money.allocation;
  exports io.genfin.money.percentage;
  exports io.genfin.money.tax;
  exports io.genfin.money.format;
  exports io.genfin.money.serialization;
  exports io.genfin.money.config;
  exports io.genfin.money.factory;
  exports io.genfin.money.spi;
  exports io.genfin.money.port.currency;
  exports io.genfin.money.port.arithmetic;
  exports io.genfin.money.port.precision;
  exports io.genfin.money.port.rounding;
  exports io.genfin.money.port.conversion;
  exports io.genfin.money.port.allocation;
  exports io.genfin.money.port.tax;
  exports io.genfin.money.port.format;
}
