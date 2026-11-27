package de.goafestival.shop.service.medusa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around Medusa's Admin API, used only for the Vorbestellungen page
 * (AdminExportController) - everything else in this app talks to the public Store API
 * (MedusaStoreClient). Needs a Secret API Key (Admin Dashboard: Settings > API Key
 * Management > Secret API Keys), not the Store API's publishable key.
 *
 * Secret API keys are deliberately NOT accepted as a Bearer token by Medusa (verified against
 * @medusajs/framework's authenticate-middleware.js) - only as HTTP Basic auth, and specifically
 * as the raw "sk_..." value after "Basic ", not base64-encoded "sk_...:" the way real HTTP
 * Basic auth normally works. Medusa's own middleware special-cases exactly this string shape.
 */
@Service
public class MedusaAdminClient {

    private final RestClient restClient;
    private final boolean enabled;

    public MedusaAdminClient(MedusaProperties properties) {
        this.enabled = !properties.getAdminSecretApiKey().isBlank();
        this.restClient = RestClient.builder()
                .baseUrl(properties.getStoreApiUrl())
                .defaultHeader("Authorization", "Basic " + properties.getAdminSecretApiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /** Whether a Secret API Key is configured at all. */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Every order with the fields the Abholliste export needs, paginated through
     * /admin/orders until exhausted (its default fields are far narrower than this, so they're
     * requested explicitly rather than with Store API's "+field" additive syntax).
     */
    public List<MedusaAdminOrder> listAllOrders() {
        List<MedusaAdminOrder> all = new ArrayList<>();
        int limit = 100;
        int offset = 0;
        while (true) {
            int currentOffset = offset;
            List<MedusaAdminOrder> page = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/admin/orders")
                            .queryParam("limit", limit)
                            .queryParam("offset", currentOffset)
                            .queryParam("order", "id")
                            .queryParam("fields", "id,display_id,email,status,shipping_address.first_name,"
                                    + "shipping_address.last_name,items.id,items.product_title,items.variant_title,"
                                    + "items.quantity,items.detail.fulfilled_quantity,items.product.metadata")
                            .build())
                    .retrieve()
                    .body(OrdersResponse.class)
                    .orders();
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < limit) {
                break;
            }
            offset += limit;
        }
        return all;
    }

    /**
     * Creates a Medusa fulfillment for exactly these order lines/quantities - this is the same
     * action an admin would otherwise do by hand in the Medusa Admin Dashboard (Orders > order >
     * "Fulfill items"), just triggered from the Vorbestellungen page instead. Deliberately not
     * wrapped in any local "done" flag of our own: Medusa's own fulfilled_quantity is the only
     * source of truth for what's been handed over, so the two can never drift apart (see
     * AdminExportController.outstandingPickupRows()). location_id is left unset - Medusa falls
     * back to the stock location linked to the order's own shipping option.
     */
    public void fulfillItems(String orderId, List<FulfillmentItem> items) {
        restClient.post()
                .uri("/admin/orders/{id}/fulfillments", orderId)
                .body(new FulfillmentRequest(items))
                .retrieve()
                .toBodilessEntity();
    }

    public record FulfillmentItem(String id, int quantity) {
    }

    private record FulfillmentRequest(List<FulfillmentItem> items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrdersResponse(List<MedusaAdminOrder> orders) {
    }
}
