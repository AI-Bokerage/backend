module io.genfin.pricing {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;

  exports io.genfin.pricing.id;
  exports io.genfin.pricing.pricing;
  exports io.genfin.pricing.lifecycle;
  exports io.genfin.pricing.port.lifecycle;
  exports io.genfin.pricing.catalog;
  exports io.genfin.pricing.port.catalog;
  exports io.genfin.pricing.price;
  exports io.genfin.pricing.calculation;
  exports io.genfin.pricing.pipeline;
  exports io.genfin.pricing.port.calculation;
  exports io.genfin.pricing.port.pipeline;
  exports io.genfin.pricing.discount;
  exports io.genfin.pricing.port.discount;
  exports io.genfin.pricing.promotion;
  exports io.genfin.pricing.port.promotion;
  exports io.genfin.pricing.coupon;
  exports io.genfin.pricing.port.coupon;
  exports io.genfin.pricing.credit;
  exports io.genfin.pricing.port.credit;
  exports io.genfin.pricing.quote;
  exports io.genfin.pricing.port.quote;
  exports io.genfin.pricing.rule;
  exports io.genfin.pricing.port.rule;
  exports io.genfin.pricing.strategy;
  exports io.genfin.pricing.port.strategy;
  exports io.genfin.pricing.tax;
  exports io.genfin.pricing.validation;
  exports io.genfin.pricing.port.validation;
  exports io.genfin.pricing.config;
  exports io.genfin.pricing.event;
  exports io.genfin.pricing.spi;
  exports io.genfin.pricing.serialization;
}
