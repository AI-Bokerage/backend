module io.genfin.document {
  requires transitive io.genfin.api;
  requires org.apache.pdfbox;

  exports io.genfin.document.api.identity;
  exports io.genfin.document.api.model;
  exports io.genfin.document.api.compose;
  exports io.genfin.document.api.result;
  exports io.genfin.document.api.exception;
  exports io.genfin.document.api.placeholder;
  exports io.genfin.document.api.template;
  exports io.genfin.document.api.brand;
  exports io.genfin.document.api.letterhead;
  exports io.genfin.document.api.qr;
  exports io.genfin.document.api.layout;
  exports io.genfin.document.api.watermark;
  exports io.genfin.document.api.preview;
  exports io.genfin.document.api.validation;
  exports io.genfin.document.api.config;
  exports io.genfin.document.api.event;
  exports io.genfin.document.port;
  exports io.genfin.document.spi;
}
