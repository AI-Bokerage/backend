package io.genfin.reconciliation.tolerance;

/**
 * A configurable allowance for variance between two sides of a reconciliation comparison. A
 * tolerance never carries a hardcoded threshold anywhere in this module — every instance is built
 * from configuration ({@code ToleranceConfiguration}, added in a later stage) and interpreted by a
 * {@code ToleranceCalculator}.
 *
 * <p>Sealed to the dimensions the engine understands structurally (amount, date, percentage,
 * currency) plus one open escape hatch, {@link CustomTolerance}, whose actual rule is resolved
 * through the {@code CustomToleranceRuleRegistry} extension point rather than added as a new kind
 * here.
 */
public sealed interface Tolerance
    permits AmountTolerance,
        DateTolerance,
        PercentageTolerance,
        CurrencyTolerance,
        CustomTolerance {

  ToleranceType type();
}
