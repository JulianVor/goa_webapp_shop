package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaProduct(
        String id,
        String title,
        String description,
        String thumbnail,
        List<Image> images,
        List<Variant> variants,
        Map<String, Object> metadata) {

    private static final DateTimeFormatter PREORDER_DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMANY);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Variant(String id, String title, CalculatedPrice calculated_price, Integer inventory_quantity,
                           Boolean allow_backorder) {

        /**
         * Null (not zero) means "not stock-managed for this variant" (Medusa only sets this
         * field for variants with manage_inventory=true - see MedusaStoreClient's
         * "+variants.inventory_quantity" field request) - i.e. unlimited, nothing to warn
         * about. Distinct from an actual 0, which does mean sold out - unless backorders are
         * allowed, in which case 0 (or even negative - already-placed backorders) is normal,
         * expected, and still orderable, so no stock hint is shown at all.
         */
        public boolean isLowStock() {
            return !Boolean.TRUE.equals(allow_backorder) && inventory_quantity != null && inventory_quantity <= 5;
        }

        public boolean isSoldOut() {
            return !Boolean.TRUE.equals(allow_backorder) && inventory_quantity != null && inventory_quantity <= 0;
        }

        /** The variant dropdown's option text, with a stock hint appended where relevant. */
        public String optionLabel() {
            if (isSoldOut()) {
                return title + " (ausverkauft)";
            }
            if (isLowStock()) {
                return title + " (nur noch " + inventory_quantity + ")";
            }
            return title;
        }
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

    /**
     * Preorder config lives in Medusa's plain product metadata, not a dedicated field or
     * custom module - this is deliberately configuration, not a schema change, so admins set
     * it per product from the ordinary Admin Dashboard "Metadata" section:
     *   {"preorder": true, "preorder_deadline": "2027-06-01T00:00:00+02:00", "pickup_only": true}
     * A product missing this, or with "preorder" not exactly true, is treated as a normal,
     * always-available stock item - this feature never affects the shop's other products.
     */
    public boolean isPreorder() {
        return metadata != null && Boolean.TRUE.equals(metadata.get("preorder"));
    }

    public boolean isPickupOnly() {
        return metadata != null && Boolean.TRUE.equals(metadata.get("pickup_only"));
    }

    /** Null if not a preorder product, or the configured deadline is missing/unparseable. */
    public OffsetDateTime preorderDeadline() {
        if (metadata == null) {
            return null;
        }
        Object raw = metadata.get("preorder_deadline");
        if (!(raw instanceof String value) || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String preorderDeadlineFormatted() {
        OffsetDateTime deadline = preorderDeadline();
        return deadline != null ? deadline.format(PREORDER_DEADLINE_FORMAT) : null;
    }

    /**
     * Past its preorder deadline - the storefront hides such products entirely (see
     * StorefrontController) rather than just disabling ordering, so the cutoff also acts as
     * a hard "unpublish" date admins don't have to remember to action manually. A product
     * with a deadline that fails to parse is treated as NOT expired (fail open towards still
     * showing it, rather than silently hiding a product over a typo'd date).
     */
    public boolean isPreorderExpired() {
        if (!isPreorder()) {
            return false;
        }
        OffsetDateTime deadline = preorderDeadline();
        return deadline != null && OffsetDateTime.now().isAfter(deadline);
    }
}
