package de.goafestival.shop.web.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    /**
     * Public URL of the Medusa Admin Dashboard (goa_webapp_shop_admin) - where products,
     * variants, prices, inventory and orders are actually managed. This app deliberately
     * doesn't rebuild that; it just links to it.
     */
    private final String medusaAdminUrl;

    public AdminDashboardController(@Value("${app.medusa.admin-url:http://localhost:9000/app}") String medusaAdminUrl) {
        this.medusaAdminUrl = medusaAdminUrl;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("medusaAdminUrl", medusaAdminUrl);
        return "admin/dashboard";
    }
}
