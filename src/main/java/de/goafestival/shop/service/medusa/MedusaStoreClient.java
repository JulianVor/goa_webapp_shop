package de.goafestival.shop.service.medusa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaCompleteCartResult;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import de.goafestival.shop.service.medusa.dto.MedusaRegion;
import de.goafestival.shop.service.medusa.dto.MedusaShippingOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin server-side wrapper around Medusa's public Store API (goa_webapp_shop_admin).
 * This app holds no product/price/cart data of its own - every call here hits Medusa
 * directly, so the storefront always reflects whatever is currently configured in the
 * Medusa Admin Dashboard.
 */
@Service
@EnableConfigurationProperties(MedusaProperties.class)
public class MedusaStoreClient {

    private static final Logger log = LoggerFactory.getLogger(MedusaStoreClient.class);

    private final RestClient restClient;
    private final MedusaProperties properties;

    /** Resolved once and reused - a festival shop doesn't need per-request region lookups. */
    private volatile MedusaRegion cachedDefaultRegion;

    public MedusaStoreClient(MedusaProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getStoreApiUrl())
                .defaultHeader("x-publishable-api-key", properties.getPublishableApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * The region used for anonymous storefront visitors - the one whose countries include
     * app.medusa.default-country-code, or the first region Medusa returns if none match
     * (e.g. only one region exists yet, which is the common case for a small shop).
     */
    public MedusaRegion defaultRegion() {
        MedusaRegion region = cachedDefaultRegion;
        if (region != null) {
            return region;
        }
        List<MedusaRegion> regions = restClient.get()
                .uri("/store/regions")
                .retrieve()
                .body(RegionsResponse.class)
                .regions();
        if (regions == null || regions.isEmpty()) {
            throw new IllegalStateException("Medusa hat keine Region konfiguriert - "
                    + "im Medusa Admin unter Settings > Regions mindestens eine Region anlegen.");
        }
        region = regions.stream()
                .filter(r -> r.coversCountry(properties.getDefaultCountryCode()))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Keine Medusa-Region deckt Land '{}' ab - verwende die erste verfügbare Region '{}'.",
                            properties.getDefaultCountryCode(), regions.get(0).name());
                    return regions.get(0);
                });
        cachedDefaultRegion = region;
        return region;
    }

    public List<MedusaProduct> listProducts() {
        MedusaRegion region = defaultRegion();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/store/products")
                        .queryParam("region_id", region.id())
                        .queryParam("limit", 20)
                        // "+" adds to Medusa's default field set rather than replacing it -
                        // metadata (preorder feature) and variants.inventory_quantity (stock
                        // cap on the add-to-cart/quantity form) aren't returned by default.
                        .queryParam("fields", "+metadata,+variants.inventory_quantity")
                        .build())
                .retrieve()
                .body(ProductsResponse.class)
                .products();
    }

    public MedusaProduct getProduct(String productId) {
        MedusaRegion region = defaultRegion();
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/store/products/{id}")
                        .queryParam("region_id", region.id())
                        .queryParam("fields", "+metadata,+variants.inventory_quantity")
                        .build(productId))
                .retrieve()
                .body(ProductResponse.class)
                .product();
    }

    public MedusaCart createCart() {
        MedusaRegion region = defaultRegion();
        return restClient.post()
                .uri("/store/carts")
                .body(Map.of("region_id", region.id()))
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    public MedusaCart getCart(String cartId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/store/carts/{id}")
                        // Medusa's default cart fields don't include a shipping method's
                        // display name (only its shipping_option_id), nor a line item's
                        // product's shipping profile - needed to tell which cart items need
                        // which shipping method when they don't all share one (see
                        // CheckoutController - e.g. a "pickup only" item mixed with a mailed one).
                        .queryParam("fields", "+shipping_methods.name,"
                                + "+items.product.shipping_profile.id,+items.product.shipping_profile.name")
                        .build(cartId))
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    public MedusaCart addLineItem(String cartId, String variantId, int quantity) {
        return restClient.post()
                .uri("/store/carts/{id}/line-items", cartId)
                .body(Map.of("variant_id", variantId, "quantity", quantity))
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    public MedusaCart updateLineItemQuantity(String cartId, String lineItemId, int quantity) {
        return restClient.post()
                .uri("/store/carts/{cartId}/line-items/{lineItemId}", cartId, lineItemId)
                .body(Map.of("quantity", quantity))
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    public void removeLineItem(String cartId, String lineItemId) {
        restClient.delete()
                .uri("/store/carts/{cartId}/line-items/{lineItemId}", cartId, lineItemId)
                .retrieve()
                .toBodilessEntity();
    }

    /** Email + shipping/billing address - see CheckoutController's address step. */
    public MedusaCart updateCart(String cartId, Map<String, Object> body) {
        return restClient.post()
                .uri("/store/carts/{id}", cartId)
                .body(body)
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    /**
     * Returns EVERY shipping option reachable from the cart's sales channel, regardless of
     * which of the cart's items (if any) actually belong to that option's shipping profile -
     * Medusa does not filter this down to "only what's relevant" itself (verified directly
     * against its listShippingOptionsForCartWorkflow source: it filters by fulfillment set,
     * never by the cart's own line items' shipping profiles). Grouping/filtering by
     * shipping_profile_id against the cart's items is this app's job - see CheckoutController.
     */
    public List<MedusaShippingOption> listShippingOptions(String cartId) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/store/shipping-options")
                        .queryParam("cart_id", cartId)
                        // None of these three are confirmed default fields for this route -
                        // requesting all defensively. MedusaShippingOption.price() prefers
                        // calculated_price over the plain amount, whichever is set.
                        .queryParam("fields", "+calculated_price,+amount,+shipping_profile_id")
                        .build())
                .retrieve()
                .body(ShippingOptionsResponse.class)
                .shipping_options();
    }

    public MedusaCart addShippingMethod(String cartId, String shippingOptionId) {
        return restClient.post()
                .uri("/store/carts/{id}/shipping-methods", cartId)
                .body(Map.of("option_id", shippingOptionId))
                .retrieve()
                .body(CartResponse.class)
                .cart();
    }

    /** Reuses an existing payment collection for the cart if Medusa already has one. */
    public String createPaymentCollection(String cartId) {
        return restClient.post()
                .uri("/store/payment-collections")
                .body(Map.of("cart_id", cartId))
                .retrieve()
                .body(PaymentCollectionResponse.class)
                .payment_collection()
                .id();
    }

    public MedusaCart.PaymentCollection initPaymentSession(String paymentCollectionId, String providerId) {
        return restClient.post()
                .uri("/store/payment-collections/{id}/payment-sessions", paymentCollectionId)
                .body(Map.of("provider_id", providerId))
                .retrieve()
                .body(PaymentCollectionResponse.class)
                .payment_collection();
    }

    /**
     * Returns 200 for both a successfully placed order AND a recoverable failure (e.g.
     * payment not actually authorized yet) - see MedusaCompleteCartResult, which carries the
     * distinguishing "type" field. Never throws for that recoverable case.
     */
    public MedusaCompleteCartResult completeCart(String cartId) {
        return restClient.post()
                .uri("/store/carts/{id}/complete", cartId)
                .retrieve()
                .body(MedusaCompleteCartResult.class);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RegionsResponse(List<MedusaRegion> regions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductsResponse(List<MedusaProduct> products) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductResponse(MedusaProduct product) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CartResponse(MedusaCart cart) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ShippingOptionsResponse(List<MedusaShippingOption> shipping_options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PaymentCollectionResponse(MedusaCart.PaymentCollection payment_collection) {
    }
}
