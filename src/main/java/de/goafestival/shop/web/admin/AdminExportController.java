package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaAdminClient;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Vorbestellungen" (preorder/pickup worklist): which items are still owed to a customer at the
 * festival. Shop-specific business logic (which items are pickup-only, see
 * MedusaProduct.isPickupOnly()) Medusa's own Admin Dashboard has no concept of, so it gets its
 * own page here instead. A plain bookkeeping export of completed orders does NOT need custom
 * code, though - Medusa's Admin Dashboard already has a built-in "Export" action on the Orders
 * list (Orders &gt; "..." menu), so that one isn't duplicated here.
 */
@Controller
public class AdminExportController {

    private static final Logger log = LoggerFactory.getLogger(AdminExportController.class);

    private final MedusaAdminClient medusaAdminClient;

    public AdminExportController(MedusaAdminClient medusaAdminClient) {
        this.medusaAdminClient = medusaAdminClient;
    }

    /** One row per order with still-outstanding pickup items, rendered as a table with a CSV-export button above it. */
    @GetMapping("/admin/vorbestellungen")
    public String vorbestellungen(Model model) {
        model.addAttribute("enabled", medusaAdminClient.isEnabled());
        model.addAttribute("rows", medusaAdminClient.isEnabled() ? outstandingPickupRows() : List.of());
        return "admin/vorbestellungen";
    }

    /**
     * Marks a whole order's outstanding pickup items as fulfilled - directly in Medusa (the same
     * action an admin would otherwise do by hand in the Medusa Admin Dashboard), not in a
     * flag of our own. That way there is only ever one source of truth for what's been handed
     * over, so this page's state and the Medusa Admin's can never silently drift apart.
     */
    @PostMapping("/admin/vorbestellungen/{orderId}/fertig")
    public String markFertig(@PathVariable String orderId, RedirectAttributes redirectAttributes) {
        List<MedusaAdminOrder.Item> outstanding = medusaAdminClient.listAllOrders().stream()
                .filter(order -> orderId.equals(order.id()))
                .findFirst()
                .map(AdminExportController::outstandingPickupItems)
                .orElse(List.of());
        if (outstanding.isEmpty()) {
            redirectAttributes.addFlashAttribute("success", "Bestellung war schon vollständig abgeholt.");
            return "redirect:/admin/vorbestellungen";
        }
        List<MedusaAdminClient.FulfillmentItem> fulfillmentItems = outstanding.stream()
                .map(item -> new MedusaAdminClient.FulfillmentItem(item.id(), item.outstandingQuantity()))
                .toList();
        try {
            medusaAdminClient.fulfillItems(orderId, fulfillmentItems);
            redirectAttributes.addFlashAttribute("success", "Bestellung als fertig markiert.");
        } catch (RestClientException e) {
            log.error("Konnte Bestellung {} nicht in Medusa als erfüllt markieren", orderId, e);
            redirectAttributes.addFlashAttribute("error",
                    "Fehler beim Markieren in Medusa - bitte stattdessen im Medusa Admin prüfen.");
        }
        return "redirect:/admin/vorbestellungen";
    }

    @GetMapping("/admin/export/abholung.csv")
    public void abholungCsv(HttpServletResponse response) throws IOException {
        if (!medusaAdminClient.isEnabled()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "Kein Medusa Admin API Key konfiguriert - MEDUSA_ADMIN_API_KEY ist leer.");
            return;
        }
        response.setContentType("text/csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"abholung.csv\"");

        PrintWriter writer = response.getWriter();
        // UTF-8 BOM - without it, Excel on Windows (the likely reader here) guesses Latin-1 and
        // mangles umlauts in names/sizes. Semicolon, not comma, is the CSV delimiter German
        // Excel expects (comma is the decimal separator there).
        writer.write('﻿');
        writer.write("Bestellnummer;Name;Produkt;Größen\n");
        for (PickupRow row : outstandingPickupRows()) {
            writer.write(csvField(String.valueOf(row.bestellnummer())));
            writer.write(';');
            writer.write(csvField(row.name()));
            writer.write(';');
            writer.write(csvField(row.produkt()));
            writer.write(';');
            writer.write(csvField(row.groesse()));
            writer.write('\n');
        }
    }

    /**
     * One row per order that still has outstanding pickup items - not per item, so an order with
     * e.g. two M and three XL shirts becomes a single row with "2x M, 3x XL" rather than two or
     * five separate ones. An order drops off this list on its own once all its pickup items are
     * marked fulfilled in Medusa - either via the "Als fertig markieren" button here or directly
     * in the Medusa Admin, both end up at the same Medusa state - and a canceled order is
     * excluded too (nothing to hand over for it). Shared by both the HTML page and the CSV
     * export so the two can never silently drift apart on what counts as "outstanding".
     */
    private List<PickupRow> outstandingPickupRows() {
        List<PickupRow> rows = new ArrayList<>();
        for (MedusaAdminOrder order : medusaAdminClient.listAllOrders()) {
            if (order.isCanceled() || order.items() == null) {
                continue;
            }
            List<MedusaAdminOrder.Item> outstanding = outstandingPickupItems(order);
            if (outstanding.isEmpty()) {
                continue;
            }
            Set<String> products = new LinkedHashSet<>();
            Map<String, Integer> outstandingBySize = new LinkedHashMap<>();
            for (MedusaAdminOrder.Item item : outstanding) {
                products.add(item.product_title());
                outstandingBySize.merge(item.variant_title(), item.outstandingQuantity(), Integer::sum);
            }
            String groessen = outstandingBySize.entrySet().stream()
                    .map(entry -> entry.getValue() + "x " + entry.getKey())
                    .collect(Collectors.joining(", "));
            rows.add(new PickupRow(order.id(), order.display_id(), order.customerName(),
                    String.join(", ", products), groessen));
        }
        return rows;
    }

    private static List<MedusaAdminOrder.Item> outstandingPickupItems(MedusaAdminOrder order) {
        if (order.items() == null) {
            return List.of();
        }
        return order.items().stream()
                .filter(item -> item.isPickupOnly() && item.outstandingQuantity() > 0)
                .toList();
    }

    public record PickupRow(String orderId, Long bestellnummer, String name, String produkt, String groesse) {
    }

    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(";") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
