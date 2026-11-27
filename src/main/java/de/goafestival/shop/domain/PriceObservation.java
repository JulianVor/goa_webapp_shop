package de.goafestival.shop.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One recorded price for one Medusa variant, at one point in time - the shop's own,
 * independent record of what a variant actually cost, built up by PriceHistoryService.
 * This is the only legally usable basis for a "price reduction" claim (§11 PAngV: the lowest
 * price actually applied in the last 30 days) - Medusa's own Price List "original_amount" is
 * whatever an admin typed in, not a verified history of prices actually charged, so it can't
 * be trusted for this on its own. A row is written only when the observed price differs from
 * the previous one for that variant (see PriceHistoryService), not on every run.
 */
@Entity
@Table(name = "price_observation")
public class PriceObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String variantId;

    private BigDecimal amount;

    private String currencyCode;

    private Instant observedAt;

    public Long getId() {
        return id;
    }

    public String getVariantId() {
        return variantId;
    }

    public void setVariantId(String variantId) {
        this.variantId = variantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(Instant observedAt) {
        this.observedAt = observedAt;
    }
}
