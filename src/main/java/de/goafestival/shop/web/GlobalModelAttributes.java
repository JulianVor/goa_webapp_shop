package de.goafestival.shop.web;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes global, page-independent data available in every template, public and admin alike. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final ShopSettingsService shopSettingsService;

    public GlobalModelAttributes(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @ModelAttribute("shopSettings")
    public ShopSettings shopSettings() {
        return shopSettingsService.get();
    }
}
