package de.goafestival.shop.config;

import de.goafestival.shop.service.ShopSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Redirects every public page to /wartung while the shop is still being set up in Medusa
 * (see ShopSettings.maintenanceMode, toggled at /admin/settings). Registered in WebConfig
 * with /admin/**, /login and the static resource paths excluded, so the operator can
 * always reach the admin area to turn it off again.
 */
@Component
public class MaintenanceModeInterceptor implements HandlerInterceptor {

    public static final String MAINTENANCE_PATH = "/wartung";

    private final ShopSettingsService shopSettingsService;

    public MaintenanceModeInterceptor(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!shopSettingsService.get().isMaintenanceMode() || MAINTENANCE_PATH.equals(request.getRequestURI())) {
            return true;
        }
        response.sendRedirect(MAINTENANCE_PATH);
        return false;
    }
}
