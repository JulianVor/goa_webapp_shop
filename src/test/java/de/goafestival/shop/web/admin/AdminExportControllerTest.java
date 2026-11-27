package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaAdminClient;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminExportControllerTest {

    private final MedusaAdminClient medusaAdminClient = mock(MedusaAdminClient.class);
    private final AdminExportController controller = new AdminExportController(medusaAdminClient);

    @Test
    void onlyListsPickupItemsThatAreNotYetFullyHandedOver() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_6", 6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 1, 0, true),   // outstanding pickup -> listed
                        item("li_2", "Goa 2026 T-Shirt", "L", 1, 1, true),   // already handed over -> skipped
                        item("li_3", "Versand-Sticker", null, 2, 0, false)  // mailed, not pickup -> skipped
                ))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("6;Mia Beispiel;Goa 2026 T-Shirt;1x M"));
        assertFalse(csv.contains("1x L"), "bereits abgeholte Größe L sollte nicht auftauchen");
        assertFalse(csv.contains("Sticker"), "Versandartikel sollten nicht auftauchen");
        assertTrue(response.getContentType().startsWith("text/csv"));
    }

    @Test
    void aggregatesMultipleSizesOfTheSameOrderIntoOneRow() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_9", 9L, "Lea", "Beispiel", "lea@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 2, 0, true),
                        item("li_2", "Goa 2026 T-Shirt", "XL", 3, 0, true)))));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.abholungCsv(response);

        String csv = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("9;Lea Beispiel;Goa 2026 T-Shirt;2x M, 3x XL"), csv);
        assertEquals(2, csv.split("\n").length, "pro Bestellung nur eine Zeile");
    }

    @Test
    void quotesFieldsThatContainTheDelimiter() throws Exception {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_7", 7L, "Jan; Schmidt", null, "jan@example.com", List.of(
                        item("li_1", "Shirt \"Special\"", "M", 1, 0, true)))));
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
                order("order_8", 8L, "Max", "Mustermann", "max@example.com", "canceled", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 1, 0, true)))));
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

    @Test
    void markFertigFulfillsExactlyTheOutstandingPickupItemsInMedusa() {
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_6", 6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 2, 0, true),
                        item("li_2", "Goa 2026 T-Shirt", "L", 1, 1, true),   // already fulfilled -> not resent
                        item("li_3", "Versand-Sticker", null, 2, 0, false)))));  // not pickup -> not sent
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.markFertig("order_6", redirectAttributes);

        verify(medusaAdminClient).fulfillItems("order_6", List.of(new MedusaAdminClient.FulfillmentItem("li_1", 2)));
        assertEquals("redirect:/admin/vorbestellungen", view);
        assertEquals("Bestellung als fertig markiert.", redirectAttributes.getFlashAttributes().get("success"));
    }

    @Test
    void markFertigSkipsMedusaCallWhenNothingIsOutstanding() {
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_6", 6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 1, 1, true)))));
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        controller.markFertig("order_6", redirectAttributes);

        verify(medusaAdminClient, never()).fulfillItems(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void markFertigReportsAnErrorInsteadOfSilentlyFailingWhenMedusaRejectsIt() {
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_6", 6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 1, 0, true)))));
        doThrow(new RestClientException("boom")).when(medusaAdminClient).fulfillItems("order_6", List.of(new MedusaAdminClient.FulfillmentItem("li_1", 1)));
        RedirectAttributes redirectAttributes = new RedirectAttributesModelMap();

        controller.markFertig("order_6", redirectAttributes);

        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        assertFalse(redirectAttributes.getFlashAttributes().containsKey("success"));
    }

    @Test
    void vorbestellungenPageShowsTheSameRowsAsTheCsv() {
        when(medusaAdminClient.isEnabled()).thenReturn(true);
        when(medusaAdminClient.listAllOrders()).thenReturn(List.of(
                order("order_6", 6L, "Mia", "Beispiel", "mia@example.com", List.of(
                        item("li_1", "Goa 2026 T-Shirt", "M", 1, 0, true),
                        item("li_2", "Goa 2026 T-Shirt", "L", 1, 1, true)))));
        Model model = new ExtendedModelMap();

        String view = controller.vorbestellungen(model);

        assertEquals("admin/vorbestellungen", view);
        assertEquals(Boolean.TRUE, model.getAttribute("enabled"));
        @SuppressWarnings("unchecked")
        List<AdminExportController.PickupRow> rows = (List<AdminExportController.PickupRow>) model.getAttribute("rows");
        assertEquals(1, rows.size());
        assertEquals("1x M", rows.get(0).groesse());
        assertEquals("order_6", rows.get(0).orderId());
    }

    @Test
    void vorbestellungenPageShowsNoRowsWhenNotConfigured() {
        when(medusaAdminClient.isEnabled()).thenReturn(false);
        Model model = new ExtendedModelMap();

        controller.vorbestellungen(model);

        assertEquals(Boolean.FALSE, model.getAttribute("enabled"));
        assertTrue(((List<?>) model.getAttribute("rows")).isEmpty());
    }

    private static MedusaAdminOrder order(String id, long displayId, String firstName, String lastName, String email,
                                           List<MedusaAdminOrder.Item> items) {
        return order(id, displayId, firstName, lastName, email, "pending", items);
    }

    private static MedusaAdminOrder order(String id, long displayId, String firstName, String lastName, String email,
                                           String status, List<MedusaAdminOrder.Item> items) {
        return new MedusaAdminOrder(id, displayId, email, status, new MedusaAdminOrder.Address(firstName, lastName), items);
    }

    private static MedusaAdminOrder.Item item(String id, String productTitle, String variantTitle, int quantity,
                                               int fulfilledQuantity, boolean pickupOnly) {
        MedusaAdminOrder.Item.Product product = new MedusaAdminOrder.Item.Product(Map.of("pickup_only", pickupOnly));
        MedusaAdminOrder.Item.Detail detail = new MedusaAdminOrder.Item.Detail(fulfilledQuantity);
        return new MedusaAdminOrder.Item(id, productTitle, variantTitle, quantity, detail, product);
    }
}
