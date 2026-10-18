package io.genfin.autoconfigure.invoice;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Only exposes the two invoice-numbering knobs that correspond to a real {@code
 * InvoiceNumberGenerators} factory method. {@code numberingStrategy} defaults to {@code timestamp},
 * matching {@code InvoiceExtensions}'s own default.
 */
@ConfigurationProperties(prefix = "genfin.invoice")
public class InvoiceProperties {

  private String numberingStrategy = "timestamp";
  private String numberingSequentialTemplate;

  public String getNumberingStrategy() {
    return numberingStrategy;
  }

  public void setNumberingStrategy(String numberingStrategy) {
    this.numberingStrategy = numberingStrategy;
  }

  public String getNumberingSequentialTemplate() {
    return numberingSequentialTemplate;
  }

  public void setNumberingSequentialTemplate(String numberingSequentialTemplate) {
    this.numberingSequentialTemplate = numberingSequentialTemplate;
  }
}
