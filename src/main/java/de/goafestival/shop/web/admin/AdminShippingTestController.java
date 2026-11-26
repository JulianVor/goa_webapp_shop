package de.goafestival.shop.web.admin;

import de.goafestival.shop.service.medusa.MedusaStoreClient;
import de.goafestival.shop.service.medusa.ShippingGrouping;
import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import de.goafestival.shop.service.medusa.dto.MedusaShippingOption;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lets shop staff check, without placing a real order, which shipping methods customers would
 * actually see for a given set of products - in particular whether a "pickup_only" product (see
 * MedusaProduct.isPickupOnly()) really sits in its own, separate Shipping Profile in Medusa, or
 * is still mixed in with regularly-mailed products and would get charged shipping like them.
 * Builds a throwaway Medusa cart purely to ask Medusa's own shipping-options resolution, the
 * same way CheckoutController does for a real customer.
 */
@Controller
@RequestMapping("/admin/versand")
public class AdminShippingTestController {

    private final MedusaStoreClient medusaStoreClient;

    public AdminShippingTestController(MedusaStoreClient medusaStoreClient) {
        this.medusaStoreClient = medusaStoreClient;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("products", medusaStoreClient.listProducts());
        return "admin/shipping-test";
    }

    @PostMapping
    public String test(@RequestParam(required = false) List<String> productIds, Model model) {
        List<MedusaProduct> products = medusaStoreClient.listProducts();
        model.addAttribute("products", products);

        if (productIds == null || productIds.isEmpty()) {
            model.addAttribute("error", "Bitte wähle mindestens ein Produkt aus.");
            return "admin/shipping-test";
        }
        model.addAttribute("selectedProductIds", new HashSet<>(productIds));

        Map<String, MedusaProduct> productsById = products.stream()
                .collect(Collectors.toMap(MedusaProduct::id, p -> p));
        Set<String> pickupOnlyTitles = new HashSet<>();
        try {
            String cartId = medusaStoreClient.createCart().id();
            for (String productId : productIds) {
                MedusaProduct product = productsById.get(productId);
                if (product == null || product.variants() == null || product.variants().isEmpty()) {
                    continue;
                }
                MedusaProduct.Variant variant = product.variants().stream()
                        .filter(v -> !v.isSoldOut())
                        .findFirst()
                        .orElseGet(() -> product.variants().get(0));
                medusaStoreClient.addLineItem(cartId, variant.id(), 1);
                if (product.isPickupOnly()) {
                    pickupOnlyTitles.add(product.title());
                }
            }
            MedusaCart cart = medusaStoreClient.getCart(cartId);
            List<MedusaShippingOption> allOptions = medusaStoreClient.listShippingOptions(cartId);
            model.addAttribute("groups", ShippingGrouping.of(cart, allOptions));
            model.addAttribute("pickupOnlyTitles", pickupOnlyTitles);
            model.addAttribute("currencyCode", cart.currency_code());
        } catch (RestClientException e) {
            model.addAttribute("error", "Versandtest fehlgeschlagen: " + e.getMessage());
        }
        return "admin/shipping-test";
    }
}
