package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POST /store/carts/:id/complete answers HTTP 200 for both outcomes - a successful order
 * AND a recoverable failure (e.g. the payment wasn't actually authorized yet when this was
 * called) - distinguished only by the "type" field, never by HTTP status. Verified directly
 * against Medusa's own route source rather than assumed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaCompleteCartResult(String type, MedusaCart cart, Order order, ErrorInfo error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(String id, Long display_id, String email) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorInfo(String message, String name, String type) {
    }

    public boolean isSuccess() {
        return "order".equals(type);
    }
}
