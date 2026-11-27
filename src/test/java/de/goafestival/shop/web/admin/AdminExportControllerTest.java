package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaAdminClient;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminExportControllerTest {

    private final MedusaAdminClient medusaAdminClient = mock(MedusaAdminClient.class);
    private final AdminExportController controller = new AdminExportController(medusaAdminClient);

    @Test
    void onlyListsPickupItemsThatAreNotYetFullyHandedOver() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order(6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("Goa 2026 T-Shirt", "M", 1, 0, true),   // outstanding pickup -> listed
                        item("Goa 2026 T-Shirt", "L", 1, 1, true),   // already handed over -> skipped
                        item("Versand-Sticker", null, 2, 0, false)  // mailed, not pickup -> skipped
                ))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("6;Mia Beispiel;Goa 2026 T-Shirt;M"));
        assertFalse(csv.contains("L\n"), "bereits abgeholte Größe L sollte nicht auftauchen");
        assertFalse(csv.contains("Sticker"), "Versandartikel sollten nicht auftauchen");
        assertTrue(response.getContentType().startsWith("text/csv"));
    }

    @Test
    void quotesFieldsThatContainTheDelimiter() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order(7L, "Jan; Schmidt", null, "jan@example.com", List.of(
                        item("Shirt \"Special\"", "M", 1, 0, true)))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("\"Jan; Schmidt\""));
        assertTrue(csv.contains("\"Shirt \"\"Special\"\"\""));
    }

    @Test
    void excludesCanceledOrdersEvenWithOutstandingPickupItems() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order(8L, "Max", "Mustermann", "max@example.com", "canceled", List.of(
                        item("Goa 2026 T-Shirt", "M", 1, 0, true)))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertFalse(csv.contains("Max Mustermann"), "stornierte Bestellung sollte nicht auftauchen");
    }

    @Test
    void returns503WhenNoAdminApiKeyIsConfigured() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        assertEquals(503, response.getStatus());
    }

    private static MedusaAdminOrder order(long displayId, String firstName, String lastName, String email,
                                           List<MedusaAdminOrder.Item> items) {
        return order(displayId, firstName, lastName, email, "pending", items);
    }

    private static MedusaAdminOrder order(long displayId, String firstName, String lastName, String email,
                                           String status, List<MedusaAdminOrder.Item> items) {
        return new MedusaAdminOrder(displayId, email, status, new MedusaAdminOrder.Address(firstName, lastName), items);
    }

    private static MedusaAdminOrder.Item item(String productTitle, String variantTitle, int quantity,
                                               int fulfilledQuantity, boolean pickupOnly) {
        MedusaAdminOrder.Item.Product product = new MedusaAdminOrder.Item.Product(Map.of("pickup_only", pickupOnly));
        MedusaAdminOrder.Item.Detail detail = new MedusaAdminOrder.Item.Detail(fulfilledQuantity);
        return new MedusaAdminOrder.Item(productTitle, variantTitle, quantity, detail, product);
    }
}
