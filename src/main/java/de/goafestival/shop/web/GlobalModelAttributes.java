package de.goafestival.shop.web;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.service.ShopSettingsService;
import de.goafestival.shop.service.branding.Branding;
import de.goafestival.shop.service.branding.GoaWebappBrandingClient;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes global, page-independent data available in every template, public and admin alike. */
@ControllerAdvice
public class GlobalModelAttributes {

    private final ShopSettingsService shopSettingsService;
    private final GoaWebappBrandingClient brandingClient;

    public GlobalModelAttributes(ShopSettingsService shopSettingsService, GoaWebappBrandingClient brandingClient) {
        this.shopSettingsService = shopSettingsService;
        this.brandingClient = brandingClient;
    }

    @ModelAttribute("shopSettings")
    public ShopSettings shopSettings() {
        return shopSettingsService.get();
    }

    /** goa_webapp's current colors/fonts/background/logo - only used by the public pages' head/nav. */
    @ModelAttribute("branding")
    public Branding branding() {
        return brandingClient.current();
    }
}
