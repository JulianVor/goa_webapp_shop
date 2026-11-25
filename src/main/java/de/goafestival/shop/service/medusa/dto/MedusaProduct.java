package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaProduct(
        String id,
        String title,
        String description,
        String thumbnail,
        List<Image> images,
        List<Variant> variants) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(String id, String title, CalculatedPrice calculated_price) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalculatedPrice(BigDecimal calculated_amount, String currency_code) {
    }

    /** The lowest-priced variant's price, or null if no variant has one (e.g. no price set yet in Medusa). */
    public CalculatedPrice lowestPrice() {
        if (variants == null) {
            return null;
        }
        return variants.stream()
                .map(Variant::calculated_price)
                .filter(p -> p != null && p.calculated_amount() != null)
                .min((a, b) -> a.calculated_amount().compareTo(b.calculated_amount()))
                .orElse(null);
    }
}
