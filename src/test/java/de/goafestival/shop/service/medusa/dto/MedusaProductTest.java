package de.goafestival.shop.service.medusa.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MedusaProductTest {

    @Test
    void advertisedPriceRangeOnlyIncludesOrderableSizes() {
        var product = shirt(size("S", "10", 0, false), size("M", "15", 2, false), size("L", "18", 0, true));

        assertEquals(new BigDecimal("15"), product.lowestPrice().calculated_amount());
        assertTrue(product.hasPriceRange());
        assertFalse(product.isSoldOut());
    }

    @Test
    void aSoldOutShirtStillShowsItsPrice() {
        var product = shirt(size("S", "15", 0, false), size("M", "15.00", 0, false));

        assertTrue(product.isSoldOut());
        assertEquals(new BigDecimal("15"), product.lowestPrice().calculated_amount());
        assertFalse(product.hasPriceRange());
    }

    private MedusaProduct shirt(MedusaProduct.Variant... sizes) {
        return new MedusaProduct("shirt", "Shirt", null, null, null, List.of(), List.of(sizes), List.of(), null);
    }

    private MedusaProduct.Variant size(String title, String amount, int quantity, boolean backorders) {
        var price = new MedusaProduct.CalculatedPrice(new BigDecimal(amount), null, false, "eur");
        return new MedusaProduct.Variant(title, title, null, price, quantity, backorders);
    }
}
