package de.goafestival.shop.service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What to actually show for one variant's price - the real amount, plus (only when legally
 * justified, see PriceHistoryService) a reference amount to strike through as the "was" price.
 * Deliberately NOT based on Medusa's own Price List "original_amount"/"is sale" flags - see
 * PriceObservation's javadoc for why those alone aren't a safe basis for a reduction claim.
 */
public record PriceDisplay(BigDecimal amount, String currencyCode, BigDecimal referenceAmount) {

    public static PriceDisplay of(BigDecimal amount, String currencyCode, BigDecimal legalLowestLast30Days) {
        boolean genuineReduction = legalLowestLast30Days != null && legalLowestLast30Days.compareTo(amount) > 0;
        return new PriceDisplay(amount, currencyCode, genuineReduction ? legalLowestLast30Days : null);
    }

    public boolean isOnSale() {
        return referenceAmount != null;
    }

    /** Rounded percentage off against the reference amount. Only meaningful if isOnSale(). */
    public int discountPercent() {
        BigDecimal saved = referenceAmount.subtract(amount);
        return saved.multiply(BigDecimal.valueOf(100))
                .divide(referenceAmount, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
