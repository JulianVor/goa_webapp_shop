package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaShippingOption(String id, String name, BigDecimal amount, CalculatedPrice calculated_price) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalculatedPrice(BigDecimal calculated_amount) {
    }

    /**
     * Medusa's shipping-options route doesn't document a single definitive field for the
     * price in its default response - this prefers the computed calculated_price (reflects
     * any shipping-specific pricing rules) and falls back to the plain "amount" field.
     */
    public BigDecimal price() {
        if (calculated_price != null && calculated_price.calculated_amount() != null) {
            return calculated_price.calculated_amount();
        }
        return amount;
    }
}
