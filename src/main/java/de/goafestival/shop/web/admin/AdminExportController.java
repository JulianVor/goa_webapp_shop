package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaAdminClient;
import de.goafestival.shop.service.medusa.dto.MedusaAdminOrder;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * CSV exports Medusa's own Admin Dashboard can't produce on its own, because they need
 * shop-specific business logic (which items are pickup-only, see MedusaProduct.isPickupOnly())
 * rather than a generic Medusa concept. A plain bookkeeping export of completed orders does NOT
 * need custom code - Medusa's Admin Dashboard already has a built-in "Export" action on the
 * Orders list (Orders &gt; "..." menu), so that one isn't duplicated here.
 */
@Controller
@RequestMapping("/admin/export")
public class AdminExportController {

    private final MedusaAdminClient medusaAdminClient;

    public AdminExportController(MedusaAdminClient medusaAdminClient) {
        this.medusaAdminClient = medusaAdminClient;
    }

    /**
     * One row per still-outstanding pickup item (not per order - a mixed order with two sizes
     * needs two rows): Bestellnummer, Name, Produkt, Größe. An item drops off this list on its
     * own once an admin marks it fulfilled in the Medusa Admin after handing it over.
     */
    @GetMapping("/abholung.csv")
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
        for (MedusaAdminOrder order : medusaAdminClient.listAllOrders()) {
            if (order.items() == null) {
                continue;
            }
            for (MedusaAdminOrder.Item item : order.items()) {
                if (!item.isPickupOnly() || !item.isOutstanding()) {
                    continue;
                }
                writer.write(csvField(String.valueOf(order.display_id())));
                writer.write(';');
                writer.write(csvField(order.customerName()));
                writer.write(';');
                writer.write(csvField(item.product_title()));
                writer.write(';');
                writer.write(csvField(item.variant_title()));
                writer.write('\n');
            }
        }
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
