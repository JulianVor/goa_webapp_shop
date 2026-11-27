package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaAdminClient;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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

    private final MedusaAdminClient medusaAdminClient;

    public AdminExportController(MedusaAdminClient medusaAdminClient) {
        this.medusaAdminClient = medusaAdminClient;
    }

    /** One row per still-outstanding pickup item, rendered as a table with a CSV-export button above it. */
    @GetMapping("/admin/vorbestellungen")
    public String vorbestellungen(Model model) {
        model.addAttribute("enabled", medusaAdminClient.isEnabled());
        model.addAttribute("rows", medusaAdminClient.isEnabled() ? outstandingPickupRows() : List.of());
        return "admin/vorbestellungen";
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
        writer.write("Bestellnummer;Name;Produkt;Größe\n");
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
     * One row per still-outstanding pickup item (not per order - a mixed order with two sizes
     * needs two rows). An item drops off this list on its own once an admin marks it fulfilled
     * in the Medusa Admin after handing it over - or if the whole order gets canceled, which is
     * excluded here too (nothing to hand over for it). Shared by both the HTML page and the CSV
     * export so the two can never silently drift apart on what counts as "outstanding".
     */
    private List<PickupRow> outstandingPickupRows() {
        List<PickupRow> rows = new ArrayList<>();
        for (MedusaAdminOrder order : medusaAdminClient.listAllOrders()) {
            if (order.isCanceled() || order.items() == null) {
                continue;
            }
            for (MedusaAdminOrder.Item item : order.items()) {
                if (!item.isPickupOnly() || !item.isOutstanding()) {
                    continue;
                }
                rows.add(new PickupRow(order.display_id(), order.customerName(), item.product_title(), item.variant_title()));
            }
        }
        return rows;
    }

    public record PickupRow(Long bestellnummer, String name, String produkt, String groesse) {
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
