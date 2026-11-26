package de.goafestival.shop.web;

import de.goafestival.shop.service.mail.OrderMailService;
import de.goafestival.shop.service.medusa.MedusaStoreClient;
import de.goafestival.shop.service.medusa.StripeProperties;
import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaCompleteCartResult;
import de.goafestival.shop.service.medusa.dto.MedusaShippingOption;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Checkout, step by step: shipping address -> shipping method -> payment (an embedded
 * Stripe Payment Element, confirmed client-side via Stripe.js - see checkout.html's inline
 * script). Every step is driven off the cart's own current state (no separate session/wizard
 * state kept here), so refreshing or navigating back just re-renders whatever step the cart
 * hasn't completed yet.
 */
@Controller
@RequestMapping("/kasse")
@EnableConfigurationProperties(StripeProperties.class)
public class CheckoutController {

    private static final Logger log = LoggerFactory.getLogger(CheckoutController.class);

    private final MedusaStoreClient medusaStoreClient;
    private final StripeProperties stripeProperties;
    private final OrderMailService orderMailService;

    public CheckoutController(MedusaStoreClient medusaStoreClient, StripeProperties stripeProperties,
                               OrderMailService orderMailService) {
        this.medusaStoreClient = medusaStoreClient;
        this.stripeProperties = stripeProperties;
        this.orderMailService = orderMailService;
    }

    // fragments/nav's bag-count badge needs this on every page - including checkout-success/
    // -failed, which don't otherwise put a "cart" attribute in the model at all (see
    // StorefrontController's identical method for the full reasoning).
    @ModelAttribute("hasCartCookie")
    public boolean hasCartCookie(@CookieValue(name = CartCookie.NAME, required = false) String cartId) {
        return cartId != null;
    }

    @GetMapping
    public String checkout(@CookieValue(name = CartCookie.NAME, required = false) String cartId, Model model) {
        if (cartId == null) {
            return "redirect:/warenkorb";
        }
        MedusaCart cart = medusaStoreClient.getCart(cartId);
        if (cart == null || cart.items() == null || cart.items().isEmpty()) {
            return "redirect:/warenkorb";
        }
        model.addAttribute("cart", cart);

        if (!cart.hasShippingAddress()) {
            model.addAttribute("step", "address");
            return "checkout";
        }

        List<MedusaShippingOption> allOptions = medusaStoreClient.listShippingOptions(cartId);
        List<ShippingGroup> shippingGroups = shippingGroups(cart, allOptions);
        Set<String> requiredProfileIds = shippingGroups.stream().map(ShippingGroup::profileId).collect(Collectors.toSet());
        Set<String> selectedProfileIds = selectedShippingProfileIds(cart, allOptions);
        if (!selectedProfileIds.containsAll(requiredProfileIds)) {
            boolean allGroupsHaveOptions = shippingGroups.stream().noneMatch(g -> g.options().isEmpty());
            model.addAttribute("shippingGroups", shippingGroups);
            model.addAttribute("allGroupsHaveOptions", allGroupsHaveOptions);
            model.addAttribute("step", "shipping");
            return "checkout";
        }

        MedusaCart.PaymentSession session;
        try {
            session = paymentSession(cart, cartId);
        } catch (RestClientException e) {
            // Most likely Stripe itself rejecting the request (e.g. a misconfigured/invalid
            // API key on the Medusa side) - shown as "payment unavailable right now" rather
            // than an uncaught exception/500 page.
            log.warn("Could not create/init Stripe payment session for cart {} ({})", cartId, e.getMessage());
            session = null;
        }
        model.addAttribute("clientSecret", session != null ? session.clientSecret() : null);
        model.addAttribute("stripePublishableKey", stripeProperties.getPublishableKey());
        model.addAttribute("step", "payment");
        return "checkout";
    }

    /**
     * One radio-button group per distinct Medusa Shipping Profile represented among the
     * cart's items (e.g. a "pickup only" festival item and a regular mailed item belong to
     * different profiles and need their own shipping method each) - see
     * MedusaCart.LineItem.shippingProfileId(). The common case (everything in the cart shares
     * one profile) is just a list of exactly one group, same as before this existed.
     */
    private record ShippingGroup(String profileId, String profileName, List<String> itemTitles,
                                  List<MedusaShippingOption> options, String selectedOptionId) {
    }

    private List<ShippingGroup> shippingGroups(MedusaCart cart, List<MedusaShippingOption> allOptions) {
        Map<String, String> profileNames = new LinkedHashMap<>();
        Map<String, List<String>> itemTitlesByProfile = new LinkedHashMap<>();
        for (MedusaCart.LineItem item : cart.items()) {
            String profileId = item.shippingProfileId();
            if (profileId == null) {
                continue;
            }
            profileNames.putIfAbsent(profileId, item.shippingProfileName());
            itemTitlesByProfile.computeIfAbsent(profileId, id -> new ArrayList<>()).add(item.title());
        }
        Map<String, String> optionIdToProfileId = allOptions.stream()
                .collect(Collectors.toMap(MedusaShippingOption::id, MedusaShippingOption::shipping_profile_id));
        List<ShippingGroup> groups = new ArrayList<>();
        for (Map.Entry<String, String> entry : profileNames.entrySet()) {
            String profileId = entry.getKey();
            List<MedusaShippingOption> options = allOptions.stream()
                    .filter(o -> profileId.equals(o.shipping_profile_id()))
                    .toList();
            String selected = cart.shipping_methods() == null ? null : cart.shipping_methods().stream()
                    .filter(sm -> profileId.equals(optionIdToProfileId.get(sm.shipping_option_id())))
                    .map(MedusaCart.ShippingMethod::shipping_option_id)
                    .findFirst()
                    .orElse(null);
            groups.add(new ShippingGroup(profileId, entry.getValue(), itemTitlesByProfile.get(profileId), options, selected));
        }
        return groups;
    }

    /** Which of the cart's required shipping profiles already have a method selected for them. */
    private Set<String> selectedShippingProfileIds(MedusaCart cart, List<MedusaShippingOption> allOptions) {
        if (cart.shipping_methods() == null) {
            return Set.of();
        }
        Map<String, String> optionIdToProfileId = allOptions.stream()
                .collect(Collectors.toMap(MedusaShippingOption::id, MedusaShippingOption::shipping_profile_id));
        return cart.shipping_methods().stream()
                .map(sm -> optionIdToProfileId.get(sm.shipping_option_id()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Creates the payment collection/session on first visit to the payment step, or reuses
     * the existing one on a later visit (Medusa's own payment-collections endpoint already
     * returns the cart's existing collection instead of duplicating it).
     */
    private MedusaCart.PaymentSession paymentSession(MedusaCart cart, String cartId) {
        MedusaCart.PaymentCollection collection = cart.payment_collection();
        MedusaCart.PaymentSession existing = collection != null
                ? collection.sessionFor(stripeProperties.getProviderId())
                : null;
        if (existing != null && existing.clientSecret() != null) {
            return existing;
        }
        String collectionId = collection != null ? collection.id() : medusaStoreClient.createPaymentCollection(cartId);
        MedusaCart.PaymentCollection updated =
                medusaStoreClient.initPaymentSession(collectionId, stripeProperties.getProviderId());
        return updated.sessionFor(stripeProperties.getProviderId());
    }

    @PostMapping("/adresse")
    public String saveAddress(@CookieValue(name = CartCookie.NAME, required = false) String cartId,
                               @RequestParam String email,
                               @RequestParam String firstName,
                               @RequestParam String lastName,
                               @RequestParam String address1,
                               @RequestParam(required = false) String address2,
                               @RequestParam String postalCode,
                               @RequestParam String city,
                               @RequestParam(required = false) String phone,
                               RedirectAttributes redirectAttributes) {
        if (cartId == null) {
            return "redirect:/warenkorb";
        }
        Map<String, Object> address = new HashMap<>();
        address.put("first_name", firstName);
        address.put("last_name", lastName);
        address.put("address_1", address1);
        address.put("address_2", address2);
        address.put("postal_code", postalCode);
        address.put("city", city);
        address.put("phone", phone);
        // Single-country shop (see app.medusa.default-country-code) - no country selector.
        address.put("country_code", "de");

        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("shipping_address", address);
        try {
            medusaStoreClient.updateCart(cartId, body);
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("error", "Bitte überprüfe deine Angaben.");
        }
        return "redirect:/kasse";
    }

    /**
     * One "shippingOptionId_&lt;profileId&gt;" field per shipping group shown on the page
     * (see checkout.html) - almost always exactly one, except for a cart mixing items from
     * different shipping profiles (e.g. a "pickup only" item and a mailed one), which needs
     * one method chosen per profile.
     */
    @PostMapping("/versand")
    public String saveShipping(@CookieValue(name = CartCookie.NAME, required = false) String cartId,
                                @RequestParam Map<String, String> allParams,
                                RedirectAttributes redirectAttributes) {
        if (cartId == null) {
            return "redirect:/warenkorb";
        }
        List<String> optionIds = allParams.entrySet().stream()
                .filter(e -> e.getKey().startsWith("shippingOptionId_"))
                .map(Map.Entry::getValue)
                .toList();
        if (optionIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Bitte wähle eine Versandart aus.");
            return "redirect:/kasse";
        }
        try {
            for (String optionId : optionIds) {
                medusaStoreClient.addShippingMethod(cartId, optionId);
            }
        } catch (HttpClientErrorException e) {
            redirectAttributes.addFlashAttribute("error", "Versandart konnte nicht gespeichert werden.");
        }
        return "redirect:/kasse";
    }

    /**
     * Stripe's confirmPayment() redirects the customer's browser here after payment
     * confirmation (see checkout.html's return_url) - this is what actually places the
     * Medusa order, since the payment itself was confirmed entirely client-side via Stripe.js.
     */
    @GetMapping("/erfolg")
    public String success(@CookieValue(name = CartCookie.NAME, required = false) String cartId,
                           HttpServletResponse response, Model model) {
        if (cartId == null) {
            return "redirect:/";
        }
        MedusaCompleteCartResult result;
        try {
            result = medusaStoreClient.completeCart(cartId);
        } catch (RestClientException e) {
            // The Stripe payment itself may well have already succeeded at this point (the
            // customer only gets here after Stripe's own confirmPayment() succeeded) - this
            // is Medusa failing to place the order synchronously, not a failed payment, so
            // say so rather than implying the charge didn't go through. Medusa's payment
            // webhook (once it arrives) can still complete the order independently.
            log.warn("Could not complete cart {} after payment confirmation ({})", cartId, e.getMessage());
            model.addAttribute("error", "Deine Zahlung war erfolgreich, aber wir konnten die Bestellung nicht "
                    + "sofort bestätigen. Wir melden uns per E-Mail - bitte bestelle nicht erneut.");
            return "checkout-failed";
        }
        if (result.isSuccess()) {
            response.addHeader("Set-Cookie", ResponseCookie.from(CartCookie.NAME, "")
                    .path("/").maxAge(0).build().toString());
            model.addAttribute("order", result.order());
            orderMailService.sendOrderConfirmation(result.order());
            return "checkout-success";
        }
        model.addAttribute("error", result.error() != null
                ? result.error().message()
                : "Die Zahlung konnte nicht abgeschlossen werden.");
        return "checkout-failed";
    }
}
