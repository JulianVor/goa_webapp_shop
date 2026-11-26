package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaCart(
        String id,
        String region_id,
        String currency_code,
        String email,
        List<LineItem> items,
        BigDecimal item_total,
        BigDecimal shipping_total,
        BigDecimal tax_total,
        BigDecimal total) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(
            String id,
            String title,
            String thumbnail,
            String variant_id,
            String variant_title,
            int quantity,
            BigDecimal unit_price) {

        /**
         * Medusa's line item "subtotal" is a computed field that's only included in the
         * response if explicitly requested via a "fields" query param - simpler to just
         * compute it here from unit_price * quantity, which is always present.
         */
        public BigDecimal subtotal() {
            return unit_price == null ? BigDecimal.ZERO : unit_price.multiply(BigDecimal.valueOf(quantity));
        }
    }
}
