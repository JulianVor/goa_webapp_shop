package de.goafestival.shop.service.medusa;

import de.goafestival.shop.service.medusa.dto.MedusaCart;
import de.goafestival.shop.service.medusa.dto.MedusaShippingOption;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Groups a cart's items by the Medusa Shipping Profile they belong to (e.g. a "pickup only"
 * festival item and a regular mailed item belong to different profiles and need their own
 * shipping method each - see MedusaCart.LineItem.shippingProfileId()). The common case
 * (everything in the cart shares one profile) is just a list of exactly one group. Used by both
 * CheckoutController (the real checkout) and AdminShippingTestController (the "Versand testen"
 * diagnostic, which never actually sets a shipping method - see Group.selectedOptionId() being
 * null there).
 */
public final class ShippingGrouping {

    private ShippingGrouping() {
    }

    public record Group(String profileId, String profileName, List<String> itemTitles,
                         List<MedusaShippingOption> options, String selectedOptionId) {

        /** The option the customer actually picked for this group, or null if none yet. */
        public MedusaShippingOption selectedOption() {
            if (selectedOptionId == null) {
                return null;
            }
            return options.stream().filter(o -> selectedOptionId.equals(o.id())).findFirst().orElse(null);
        }
    }

    public static List<Group> of(MedusaCart cart, List<MedusaShippingOption> allOptions) {
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
        Map<String, String> optionIdToProfileId = optionIdToProfileId(allOptions);
        List<Group> groups = new ArrayList<>();
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
            groups.add(new Group(profileId, entry.getValue(), itemTitlesByProfile.get(profileId), options, selected));
        }
        return groups;
    }

    /** Which of the cart's required shipping profiles already have a method selected for them. */
    public static Set<String> selectedProfileIds(MedusaCart cart, List<MedusaShippingOption> allOptions) {
        if (cart.shipping_methods() == null) {
            return Set.of();
        }
        Map<String, String> optionIdToProfileId = optionIdToProfileId(allOptions);
        return cart.shipping_methods().stream()
                .map(sm -> optionIdToProfileId.get(sm.shipping_option_id()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static Map<String, String> optionIdToProfileId(List<MedusaShippingOption> allOptions) {
        return allOptions.stream()
                .collect(Collectors.toMap(MedusaShippingOption::id, MedusaShippingOption::shipping_profile_id));
    }
}
