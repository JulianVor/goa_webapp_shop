package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaCart(
        String id,
        String region_id,
        String currency_code,
        String email,
        MedusaAddress shipping_address,
        List<LineItem> items,
        List<ShippingMethod> shipping_methods,
        PaymentCollection payment_collection,
        BigDecimal item_total,
        BigDecimal shipping_total,
        BigDecimal tax_total,
        BigDecimal total) {

    public int itemCount() {
        return items == null ? 0 : items.stream().mapToInt(LineItem::quantity).sum();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LineItem(
            String id,
            String title,
            String thumbnail,
            String product_id,
            String variant_id,
            String variant_title,
            int quantity,
            BigDecimal unit_price,
            Product product) {

        /**
         * Medusa's line item "subtotal" is a computed field that's only included in the
         * response if explicitly requested via a "fields" query param - simpler to just
         * compute it here from unit_price * quantity, which is always present.
         */
        public BigDecimal subtotal() {
            return unit_price == null ? BigDecimal.ZERO : unit_price.multiply(BigDecimal.valueOf(quantity));
        }

        /**
         * Which Medusa Shipping Profile this item's product belongs to - products with
         * different profiles (e.g. a "pickup only" festival item vs. a regular mailed one)
         * need their own, separate shipping method selection - see CheckoutController.
         */
        public String shippingProfileId() {
            return product != null && product.shipping_profile() != null ? product.shipping_profile().id() : null;
        }

        public String shippingProfileName() {
            return product != null && product.shipping_profile() != null ? product.shipping_profile().name() : null;
        }
    }

    /** Only carries the one relation (shipping_profile) this app actually needs from the product. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(ShippingProfile shipping_profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShippingProfile(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ShippingMethod(String id, String shipping_option_id, String name, BigDecimal amount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentCollection(String id, List<PaymentSession> payment_sessions) {

        public PaymentSession sessionFor(String providerId) {
            if (payment_sessions == null) {
                return null;
            }
            return payment_sessions.stream()
                    .filter(s -> providerId.equals(s.provider_id()))
                    .findFirst()
                    .orElse(null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentSession(String id, String provider_id, String status, Map<String, Object> data) {

        /**
         * Stripe's full raw PaymentIntent object ends up verbatim in this session's "data" -
         * client_secret is what Stripe.js needs client-side to render/confirm the Payment
         * Element (see goa_webapp_shop_admin's payment-stripe integration).
         */
        public String clientSecret() {
            Object value = data != null ? data.get("client_secret") : null;
            return value instanceof String s ? s : null;
        }
    }

    public boolean hasShippingAddress() {
        return shipping_address != null
                && shipping_address.address_1() != null
                && !shipping_address.address_1().isBlank();
    }
}
