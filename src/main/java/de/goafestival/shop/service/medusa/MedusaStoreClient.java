package de.goafestival.shop.service.medusa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import de.goafestival.shop.service.medusa.dto.MedusaRegion;
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

    /** Resolved once and reused - a festival fanshop doesn't need per-request region lookups. */
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
                        // metadata (used for the preorder feature) isn't returned by default.
                        .queryParam("fields", "+metadata")
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
                        .queryParam("fields", "+metadata")
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
                .uri("/store/carts/{id}", cartId)
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
}
