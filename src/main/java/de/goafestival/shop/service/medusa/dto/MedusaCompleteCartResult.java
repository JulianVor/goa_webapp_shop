package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * POST /store/carts/:id/complete answers HTTP 200 for both outcomes - a successful order
 * AND a recoverable failure (e.g. the payment wasn't actually authorized yet when this was
 * called) - distinguished only by the "type" field, never by HTTP status. Verified directly
 * against Medusa's own route source rather than assumed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaCompleteCartResult(String type, MedusaCart cart, Order order, ErrorInfo error) {

    /**
     * No "fields" query param is sent for this request - the route's own default field set
     * (verified against Medusa's query-config source) already includes "*items", "total" and
     * "currency_code", so all of this comes back without asking for it explicitly.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String id, Long display_id, String email, BigDecimal total, BigDecimal shipping_total,
                         String currency_code, List<OrderItem> items) {
    }

    /** A single order line - same title/variant_title convention as MedusaCart.LineItem. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(String title, String variant_title, BigDecimal quantity, BigDecimal unit_price,
                             BigDecimal total) {

        public boolean hasDisplayVariantTitle() {
            return variant_title != null
                    && !variant_title.equals("Default option value")
                    && !variant_title.equals("Default variant");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(String message, String name, String type) {
    }

    public boolean isSuccess() {
        return "order".equals(type);
    }
}
