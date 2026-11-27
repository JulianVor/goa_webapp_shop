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
    public record Variant(String id, String title, String sku, CalculatedPrice calculated_price,
                           Integer inventory_quantity, Boolean allow_backorder) {

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
    }

    /** Use orderable variants for the advertised price while any are available. */
    public CalculatedPrice lowestPrice() {
        Variant variant = lowestPricedVariant();
        return variant != null ? variant.calculated_price() : null;
    }

    /**
     * The variant lowestPrice() advertises - exposed separately (not just its price) because
     * computing a legally safe "is this really on sale, and since when" (see PriceHistoryService)
     * needs the variant's id, not just its current price.
     */
    public Variant lowestPricedVariant() {
        if (variants == null) {
            return null;
        }
        boolean soldOut = isSoldOut();
        return variants.stream()
                .filter(variant -> soldOut || !variant.isSoldOut())
                .filter(variant -> variant.calculated_price() != null && variant.calculated_price().calculated_amount() != null)
                .min((a, b) -> a.calculated_price().calculated_amount().compareTo(b.calculated_price().calculated_amount()))
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

    /**
     * GPSR/product-safety-adjacent info (material composition, manufacturer) - also plain
     * product metadata, same deliberate choice as isPreorder() above: admins set it per
     * product's "Metadata" section in the Medusa Admin Dashboard, nothing to configure here.
     */
    public String material() {
        Object raw = metadata == null ? null : metadata.get("material");
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    public String manufacturer() {
        Object raw = metadata == null ? null : metadata.get("manufacturer");
        return raw instanceof String value && !value.isBlank() ? value : null;
    }

    private OffsetDateTime metadataDate(String key) {
        Object raw = metadata == null ? null : metadata.get(key);
        if (!(raw instanceof String value) || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** A concrete pickup window ("pickup_start"/"pickup_end" metadata), shown instead of just the badge. */
    public String pickupPeriodFormatted() {
        OffsetDateTime start = metadataDate("pickup_start");
        OffsetDateTime end = metadataDate("pickup_end");
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);
        if (start != null && end != null) {
            return start.format(dateFormat) + " – " + end.format(dateFormat);
        }
        if (start != null) {
            return "ab " + start.format(dateFormat);
        }
        if (end != null) {
            return "bis " + end.format(dateFormat);
        }
        return null;
    }
}
