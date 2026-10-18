package io.genfin.tax.api.decision;

import java.util.List;

/**
 * Open-value-type identifying the overall tax outcome of a transaction. Carries its own component
 * list so a calculator never needs an {@code instanceof}/{@code switch} to map treatment → line
 * items.
 */
public interface TaxTreatment {

  String code();

  List<TaxComponentType> components();
}
