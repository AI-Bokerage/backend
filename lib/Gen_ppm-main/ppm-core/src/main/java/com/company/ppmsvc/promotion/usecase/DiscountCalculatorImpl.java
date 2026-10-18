package com.company.ppmsvc.promotion.usecase;

import com.company.ppmsvc.promotion.model.AppliedDiscount;
import com.company.ppmsvc.promotion.model.FixedPriceDiscount;
import com.company.ppmsvc.promotion.model.FlatDiscount;
import com.company.ppmsvc.promotion.model.PercentageDiscount;
import com.company.ppmsvc.promotion.model.PriceAction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/**
 * Pure implementation of {@link DiscountCalculator}.
 *
 * <p>Rounding: both {@code discountAmount} and {@code finalAmount} are scaled
 * to {@code setScale(4, RoundingMode.HALF_EVEN)} to match the
 * {@code NUMERIC(19,4)} money columns used across PPM. {@code HALF_EVEN} (aka
 * "banker's rounding") is used rather than {@code HALF_UP} because it avoids
 * systematic upward bias when rounding a large number of discount amounts.
 *
 * <p>See {@link DiscountCalculator} for why this class is not named
 * "PromotionEngine", and why its {@code apply} signature only accepts {@link
 * PriceAction} (narrowed in Phase 4 from the flat {@code PromotionAction}) —
 * do not "helpfully" rename or widen it back.
 */
@Service
public class DiscountCalculatorImpl implements DiscountCalculator {

    private static final int MONEY_SCALE = 4;

    @Override
    public AppliedDiscount apply(BigDecimal baseAmount, PriceAction action) {
        BigDecimal discount = switch (action) {
            case PercentageDiscount pct  -> percentageDiscount(baseAmount, pct);
            case FlatDiscount flat       -> flat.amount().min(baseAmount);
            case FixedPriceDiscount fp   -> baseAmount.subtract(fp.price()).max(BigDecimal.ZERO);
        };

        discount = discount.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        BigDecimal finalAmount = baseAmount.subtract(discount).setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);

        return new AppliedDiscount(baseAmount, discount, finalAmount, action);
    }

    private static BigDecimal percentageDiscount(BigDecimal baseAmount, PercentageDiscount pct) {
        BigDecimal raw = baseAmount.multiply(pct.percentage())
            .divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_EVEN);

        if (pct.maxDiscountValue() != null) {
            raw = raw.min(pct.maxDiscountValue());
        }
        if (pct.minDiscountValue() != null) {
            raw = raw.max(pct.minDiscountValue());
        }
        return raw.min(baseAmount);
    }
}
