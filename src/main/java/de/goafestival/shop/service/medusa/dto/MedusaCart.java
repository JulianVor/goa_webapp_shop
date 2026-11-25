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
            int quantity,
            BigDecimal unit_price,
            BigDecimal subtotal) {
    }
}
