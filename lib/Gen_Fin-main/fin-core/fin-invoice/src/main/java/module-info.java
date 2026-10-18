module io.genfin.invoice {
  requires transitive io.genfin.api;
  requires transitive io.genfin.money;

  exports io.genfin.invoice.exception;
  exports io.genfin.invoice.reference;
  exports io.genfin.invoice.metadata;
  exports io.genfin.invoice.lifecycle;
  exports io.genfin.invoice.discount;
  exports io.genfin.invoice.adjustment;
  exports io.genfin.invoice.attachment;
  exports io.genfin.invoice.event;
  exports io.genfin.invoice.numbering;
  exports io.genfin.invoice.id;
  exports io.genfin.invoice.line;
  exports io.genfin.invoice.invoice;
  exports io.genfin.invoice.calculation;
  exports io.genfin.invoice.validation;
  exports io.genfin.invoice.builder;
  exports io.genfin.invoice.factory;
  exports io.genfin.invoice.config;
  exports io.genfin.invoice.serialization;
  exports io.genfin.invoice.spi;
  exports io.genfin.invoice.port.lifecycle;
  exports io.genfin.invoice.port.discount;
  exports io.genfin.invoice.port.adjustment;
  exports io.genfin.invoice.port.numbering;
  exports io.genfin.invoice.port.calculation;
  exports io.genfin.invoice.port.validation;
  exports io.genfin.invoice.port.format;
  exports io.genfin.invoice.port.reference;
  exports io.genfin.invoice.port.metadata;
  exports io.genfin.invoice.port.builder;
}
