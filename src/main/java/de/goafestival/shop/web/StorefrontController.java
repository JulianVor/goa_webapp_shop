package de.goafestival.shop.web;

import de.goafestival.shop.service.medusa.MedusaStoreClient;
import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The public storefront: product list/detail and a session-cookie-based cart, both
 * backed entirely by Medusa's Store API (see MedusaStoreClient) - this app stores no
 * product or cart data of its own.
 */
@Controller
public class StorefrontController {

    private static final String CART_COOKIE = "cart_id";

    private final MedusaStoreClient medusaStoreClient;

    public StorefrontController(MedusaStoreClient medusaStoreClient) {
        this.medusaStoreClient = medusaStoreClient;
    }

    @GetMapping("/")
    public String home(Model model) {
        List<MedusaProduct> products = medusaStoreClient.listProducts().stream()
                .filter(product -> !product.isPreorderExpired())
                .toList();
        model.addAttribute("products", products);
        return "home";
    }

    /**
     * A preorder item past its deadline is treated as gone, not just unorderable - 404
     * rather than rendering the page, so a shared/bookmarked link doesn't linger as a dead
     * end that still shows an (uncompletable) order form.
     */
    @GetMapping("/produkte/{id}")
    public String productDetail(@PathVariable String id, Model model) {
        MedusaProduct product = medusaStoreClient.getProduct(id);
        if (product.isPreorderExpired()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vorbestellfrist abgelaufen");
        }
        model.addAttribute("product", product);
        return "product-detail";
    }

    @GetMapping("/warenkorb")
    public String cart(@CookieValue(name = CART_COOKIE, required = false) String cartId, Model model) {
        MedusaCart cart = cartId != null ? medusaStoreClient.getCart(cartId) : null;
        model.addAttribute("cart", cart);
        model.addAttribute("maxQuantities", maxQuantitiesByLineItem(cart));
        return "cart";
    }

    /**
     * How high the quantity field for each line item may go - Medusa's own stock check
     * (confirmInventoryStep) ultimately decides, but without this the number input let
     * someone type/scroll well past what's actually in stock before finding out via a
     * rejected request. Null (no entry in the map) means no cap - either not stock-managed
     * or backorders are allowed for that variant.
     */
    private Map<String, Integer> maxQuantitiesByLineItem(MedusaCart cart) {
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return Map.of();
        }
        Set<String> productIds = cart.items().stream()
                .map(MedusaCart.LineItem::product_id)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        Map<String, MedusaProduct.Variant> variantsById = new HashMap<>();
        for (String productId : productIds) {
            MedusaProduct product = medusaStoreClient.getProduct(productId);
            if (product.variants() != null) {
                product.variants().forEach(v -> variantsById.put(v.id(), v));
            }
        }
        Map<String, Integer> result = new HashMap<>();
        for (MedusaCart.LineItem item : cart.items()) {
            MedusaProduct.Variant variant = variantsById.get(item.variant_id());
            if (variant != null && !Boolean.TRUE.equals(variant.allow_backorder()) && variant.inventory_quantity() != null) {
                result.put(item.id(), variant.inventory_quantity());
            }
        }
        return result;
    }

    @PostMapping("/warenkorb/hinzufuegen")
    public String addToCart(@RequestParam String variantId,
                             @RequestParam(defaultValue = "1") int quantity,
                             @CookieValue(name = CART_COOKIE, required = false) String cartId,
                             HttpServletRequest request, HttpServletResponse response,
                             RedirectAttributes redirectAttributes) {
        String effectiveCartId = cartId;
        if (effectiveCartId == null) {
            MedusaCart newCart = medusaStoreClient.createCart();
            effectiveCartId = newCart.id();
            response.addHeader("Set-Cookie", ResponseCookie.from(CART_COOKIE, effectiveCartId)
                    .path("/")
                    .maxAge(java.time.Duration.ofDays(30))
                    .httpOnly(true)
                    .sameSite("Lax")
                    .secure(request.isSecure())
                    .build().toString());
        }
        try {
            medusaStoreClient.addLineItem(effectiveCartId, variantId, quantity);
            redirectAttributes.addFlashAttribute("success", "Zum Warenkorb hinzugefügt.");
        } catch (HttpClientErrorException e) {
            // Most commonly Medusa's own stock check (confirmInventoryStep) rejecting a
            // quantity beyond what's actually available - surfaced as a flash message
            // instead of an uncaught exception/500 page.
            redirectAttributes.addFlashAttribute("error",
                    "Die gewünschte Menge ist leider nicht verfügbar. Bitte wähle eine kleinere Menge.");
        }
        return "redirect:/warenkorb";
    }

    @PostMapping("/warenkorb/menge")
    public String updateQuantity(@RequestParam String lineItemId, @RequestParam int quantity,
                                  @CookieValue(name = CART_COOKIE, required = false) String cartId,
                                  RedirectAttributes redirectAttributes) {
        if (cartId != null) {
            try {
                medusaStoreClient.updateLineItemQuantity(cartId, lineItemId, quantity);
            } catch (HttpClientErrorException e) {
                redirectAttributes.addFlashAttribute("error",
                        "Die gewünschte Menge ist leider nicht verfügbar. Bitte wähle eine kleinere Menge.");
            }
        }
        return "redirect:/warenkorb";
    }

    @PostMapping("/warenkorb/entfernen")
    public String removeFromCart(@RequestParam String lineItemId,
                                  @CookieValue(name = CART_COOKIE, required = false) String cartId) {
        if (cartId != null) {
            medusaStoreClient.removeLineItem(cartId, lineItemId);
        }
        return "redirect:/warenkorb";
    }

    @GetMapping("/wartung")
    public String maintenance() {
        return "maintenance";
    }

    /**
     * Placeholder: address/shipping-option selection and the actual Stripe payment step
     * (Medusa payment collections + Stripe Elements) aren't wired up yet - that needs a
     * real Stripe account/API keys, which weren't available at the time this was built.
     * The cart itself (via Medusa) is fully functional up to this point.
     */
    @GetMapping("/kasse")
    public String checkoutStub(@CookieValue(name = CART_COOKIE, required = false) String cartId, Model model) {
        MedusaCart cart = cartId != null ? medusaStoreClient.getCart(cartId) : null;
        model.addAttribute("cart", cart);
        return "checkout-stub";
    }

    @GetMapping("/impressum")
    public String impressum() {
        return "legal/impressum";
    }

    @GetMapping("/agb")
    public String agb() {
        return "legal/agb";
    }

    @GetMapping("/widerruf")
    public String widerruf() {
        return "legal/widerruf";
    }

    @GetMapping("/datenschutz")
    public String datenschutz() {
        return "legal/datenschutz";
    }
}
