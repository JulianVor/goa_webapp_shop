package de.goafestival.shop.web.admin;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.dto.ShopSettingsForm;
import de.goafestival.shop.service.ShopSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private final ShopSettingsService shopSettingsService;

    public AdminSettingsController(ShopSettingsService shopSettingsService) {
        this.shopSettingsService = shopSettingsService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("settingsForm", toForm(shopSettingsService.get()));
        return "admin/settings-form";
    }

    @PostMapping
    public String save(@ModelAttribute("settingsForm") ShopSettingsForm form, RedirectAttributes redirectAttributes) {
        shopSettingsService.update(form);
        redirectAttributes.addFlashAttribute("success", "Einstellungen wurden gespeichert.");
        return "redirect:/admin/settings";
    }

    private ShopSettingsForm toForm(ShopSettings settings) {
        ShopSettingsForm form = new ShopSettingsForm();
        form.setMaintenanceMode(settings.isMaintenanceMode());
        form.setImpressumText(settings.getImpressumText());
        form.setAgbText(settings.getAgbText());
        form.setWiderrufText(settings.getWiderrufText());
        form.setDatenschutzText(settings.getDatenschutzText());
        form.setContactEmail(settings.getContactEmail());
        form.setTestAccessEnabled(settings.isTestAccessEnabled());
        return form;
    }
}
