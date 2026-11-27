package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * The subset of an Admin API order (GET /admin/orders) needed for the Vorbestellungen page/CSV
 * export (see MedusaAdminClient/AdminExportController) - deliberately minimal, not a
 * general-purpose order mirror.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaAdminOrder(String id, Long display_id, String email, String status, Address shipping_address, List<Item> items) {

    /** Medusa's OrderStatus: "pending"|"completed"|"draft"|"archived"|"canceled"|"requires_action". */
    public boolean isCanceled() {
        return "canceled".equals(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Address(String first_name, String last_name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String id, String product_title, String variant_title, Integer quantity, Detail detail, Product product) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Detail(Integer fulfilled_quantity) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Product(Map<String, Object> metadata) {
            /** Same metadata flag as MedusaProduct.isPickupOnly() - admins set it per product in Medusa. */
            public boolean isPickupOnly() {
                return metadata != null && Boolean.TRUE.equals(metadata.get("pickup_only"));
            }
        }

        /** Not yet (fully) handed over at the festival - the Abholliste's only reason to list it. */
        public boolean isOutstanding() {
            return outstandingQuantity() > 0;
        }

        /** How many units of this line are still owed at the festival (0 once fully handed over). */
        public int outstandingQuantity() {
            int fulfilled = detail != null && detail.fulfilled_quantity() != null ? detail.fulfilled_quantity() : 0;
            int total = quantity != null ? quantity : 0;
            return Math.max(0, total - fulfilled);
        }

        public boolean isPickupOnly() {
            return product != null && product.isPickupOnly();
        }
    }

    public String customerName() {
        if (shipping_address == null) {
            return email;
        }
        String first = shipping_address.first_name() != null ? shipping_address.first_name() : "";
        String last = shipping_address.last_name() != null ? shipping_address.last_name() : "";
        String name = (first + " " + last).trim();
        return !name.isEmpty() ? name : email;
    }
}
