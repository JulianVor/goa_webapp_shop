package de.goafestival.shop.web.admin;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.dto.OrderConfirmationForm;
import de.goafestival.shop.dto.ShopSettingsForm;
import de.goafestival.shop.service.ShopSettingsService;
import de.goafestival.shop.service.mail.OrderMailService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private final ShopSettingsService shopSettingsService;
    private final OrderMailService orderMailService;

    public AdminSettingsController(ShopSettingsService shopSettingsService, OrderMailService orderMailService) {
        this.shopSettingsService = shopSettingsService;
        this.orderMailService = orderMailService;
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

    @GetMapping("/bestellbestaetigung")
    public String orderConfirmationForm(Model model) {
        model.addAttribute("confirmationForm", confirmationFormFromCurrent());
        model.addAttribute("mailEnabled", orderMailService.isEnabled());
        return "admin/order-confirmation";
    }

    @PostMapping("/bestellbestaetigung")
    public String saveOrderConfirmation(@Valid @ModelAttribute("confirmationForm") OrderConfirmationForm form,
                                         BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("mailEnabled", orderMailService.isEnabled());
            return "admin/order-confirmation";
        }
        orderMailService.updateConfirmationTemplate(form.getSubject(), form.getHtmlBody());
        redirectAttributes.addFlashAttribute("success", "Bestellbestätigung wurde gespeichert.");
        return "redirect:/admin/settings/bestellbestaetigung";
    }

    /** Renders the exact branded HTML the admin's current draft would produce, for the preview-before-save step. */
    @PostMapping(value = "/bestellbestaetigung/preview", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String previewOrderConfirmation(@RequestParam(defaultValue = "") String htmlBody) {
        return orderMailService.previewHtml(htmlBody);
    }

    private OrderConfirmationForm confirmationFormFromCurrent() {
        OrderConfirmationForm form = new OrderConfirmationForm();
        form.setSubject(orderMailService.getConfirmationSubject());
        form.setHtmlBody(orderMailService.getConfirmationBody());
        return form;
    }

    private ShopSettingsForm toForm(ShopSettings settings) {
        ShopSettingsForm form = new ShopSettingsForm();
        form.setMaintenanceMode(settings.isMaintenanceMode());
        form.setImpressumText(settings.getImpressumText());
        form.setAgbText(settings.getAgbText());
        form.setWiderrufText(settings.getWiderrufText());
        form.setDatenschutzText(settings.getDatenschutzText());
        form.setContactEmail(settings.getContactEmail());
        return form;
    }
}
