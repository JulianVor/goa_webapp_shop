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
        String subtitle,
        String description,
        String thumbnail,
        List<Image> images,
        List<Variant> variants,
        List<Option> options,
        Map<String, Object> metadata) {

    private static final DateTimeFormatter PREORDER_DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy 'um' HH:mm 'Uhr'", Locale.GERMANY);

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Image(String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String title) {
    }

    public String imageForCard() {
        if (thumbnail != null && !thumbnail.isBlank()) return thumbnail;
        return images == null || images.isEmpty() ? null : images.get(0).url();
    }

    public String variantLabel() {
        return options != null && options.size() == 1 && options.get(0).title() != null
                ? options.get(0).title() : "Variante";
    }

    public boolean isSoldOut() {
        return variants != null && !variants.isEmpty() && variants.stream().allMatch(Variant::isSoldOut);
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

        public boolean hasDisplayTitle() {
            return title != null && !title.isBlank() && !"Default option value".equalsIgnoreCase(title)
                    && !"Default variant".equalsIgnoreCase(title);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CalculatedPrice(
            BigDecimal calculated_amount,
            BigDecimal original_amount,
            Boolean is_calculated_price_price_list,
            String currency_code) {

        /**
         * A Price List (e.g. a "sale" type list overriding the default price) is active AND
         * actually cheaper than the regular price - Medusa still sets
         * is_calculated_price_price_list for an override-type list even when it isn't a
         * discount, so that flag alone isn't enough to label something "Sale".
         */
        public boolean isOnSale() {
            return calculated_amount != null && Boolean.TRUE.equals(is_calculated_price_price_list)
                    && original_amount != null
                    && original_amount.compareTo(calculated_amount) > 0;
        }

        /** Rounded percentage off, e.g. 25 for 20.00 -> 15.00. Only meaningful if isOnSale(). */
        public int discountPercent() {
            BigDecimal saved = original_amount.subtract(calculated_amount);
            return saved.multiply(BigDecimal.valueOf(100))
                    .divide(original_amount, 0, java.math.RoundingMode.HALF_UP)
                    .intValue();
        }
    }

    /** Use orderable variants for the advertised price while any are available. */
    public CalculatedPrice lowestPrice() {
        if (variants == null) {
            return null;
        }
        boolean soldOut = isSoldOut();
        return variants.stream()
                .filter(variant -> soldOut || !variant.isSoldOut())
                .map(Variant::calculated_price)
                .filter(p -> p != null && p.calculated_amount() != null)
                .min((a, b) -> a.calculated_amount().compareTo(b.calculated_amount()))
                .orElse(null);
    }

    public boolean hasPriceRange() {
        CalculatedPrice lowest = lowestPrice();
        if (lowest == null) return false;
        boolean soldOut = isSoldOut();
        return variants.stream()
                .filter(variant -> soldOut || !variant.isSoldOut())
                .map(Variant::calculated_price)
                .anyMatch(price -> price != null && price.calculated_amount() != null
                        && price.calculated_amount().compareTo(lowest.calculated_amount()) > 0);
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
