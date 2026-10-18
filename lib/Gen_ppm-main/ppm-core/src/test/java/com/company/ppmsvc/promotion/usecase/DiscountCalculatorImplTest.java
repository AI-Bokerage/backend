package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DiscountCalculatorImpl")
class DiscountCalculatorImplTest {

    private final DiscountCalculatorImpl calculator = new DiscountCalculatorImpl();

    @Nested
    @DisplayName("FlatDiscount")
    class Flat {

        @Test
        @DisplayName("flat amount less than base is subtracted as-is")
        void flat_lessThanBase() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("100.0000"), new FlatDiscount(new BigDecimal("20")));

            assertThat(result.discountAmount()).isEqualByComparingTo("20.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("80.0000");
        }

        @Test
        @DisplayName("flat amount greater than base is clamped to base — final amount is zero, never negative")
        void flat_greaterThanBase_clampedToBase() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("50.0000"), new FlatDiscount(new BigDecimal("200")));

            assertThat(result.discountAmount()).isEqualByComparingTo("50.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("0.0000");
        }
    }

    @Nested
    @DisplayName("PercentageDiscount")
    class Percentage {

        @Test
        @DisplayName("no caps — straight percentage of base")
        void percentage_noCaps() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("100.0000"), new PercentageDiscount(new BigDecimal("20"), null, null));

            assertThat(result.discountAmount()).isEqualByComparingTo("20.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("80.0000");
        }

        @Test
        @DisplayName("max cap — raw discount above max is capped down")
        void percentage_maxCap_capsRawDiscount() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("2000.0000"),
                new PercentageDiscount(new BigDecimal("20"), new BigDecimal("200"), null));

            // raw would be 400, capped to 200
            assertThat(result.discountAmount()).isEqualByComparingTo("200.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("1800.0000");
        }

        @Test
        @DisplayName("min cap — raw discount below min is floored up")
        void percentage_minCap_floorsRawDiscount() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("10.0000"),
                new PercentageDiscount(new BigDecimal("5"), null, new BigDecimal("2")));

            // raw would be 0.50, floored to 2
            assertThat(result.discountAmount()).isEqualByComparingTo("2.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("8.0000");
        }

        @Test
        @DisplayName("rounding — divides using HALF_EVEN scaled to 4 decimal places")
        void percentage_rounding_halfEven() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("100.0000"), new PercentageDiscount(new BigDecimal("33.335"), null, null));

            // 100 * 33.335 / 100 = 33.335 -> scale 4 = 33.3350
            assertThat(result.discountAmount()).isEqualByComparingTo("33.3350");
        }

        @Test
        @DisplayName("final amount never negative even with a cap larger than base")
        void percentage_finalAmountNeverNegative() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("10.0000"),
                new PercentageDiscount(new BigDecimal("50"), new BigDecimal("1000"), null));

            assertThat(result.discountAmount()).isEqualByComparingTo("5.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("5.0000");
            assertThat(result.finalAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("FixedPriceDiscount")
    class FixedPrice {

        @Test
        @DisplayName("base above fixed price — discount is the difference")
        void fixedPrice_baseAboveFixed_discountIsDifference() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("2000.0000"), new FixedPriceDiscount(new BigDecimal("499")));

            assertThat(result.discountAmount()).isEqualByComparingTo("1501.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("499.0000");
        }

        @Test
        @DisplayName("base below fixed price — discount floored at zero, final amount is base")
        void fixedPrice_baseBelowFixed_noDiscount() {
            AppliedDiscount result = calculator.apply(
                new BigDecimal("300.0000"), new FixedPriceDiscount(new BigDecimal("499")));

            assertThat(result.discountAmount()).isEqualByComparingTo("0.0000");
            assertThat(result.finalAmount()).isEqualByComparingTo("300.0000");
        }
    }
}
