package de.goafestival.shop.web.admin;

import de.goafestival.shop.dto.OrderConfirmationForm;
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
@RequestMapping("/admin/bestellbestaetigung")
public class AdminOrderConfirmationController {

    private final OrderMailService orderMailService;

    public AdminOrderConfirmationController(OrderMailService orderMailService) {
        this.orderMailService = orderMailService;
    }

    @GetMapping
    public String form(Model model) {
        model.addAttribute("confirmationForm", confirmationFormFromCurrent());
        model.addAttribute("mailEnabled", orderMailService.isEnabled());
        return "admin/order-confirmation";
    }

    @PostMapping
    public String save(@Valid @ModelAttribute("confirmationForm") OrderConfirmationForm form,
                        BindingResult result, Model model, RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("mailEnabled", orderMailService.isEnabled());
            return "admin/order-confirmation";
        }
        orderMailService.updateConfirmationTemplate(form.getSubject(), form.getHtmlBody());
        redirectAttributes.addFlashAttribute("success", "Bestellbestätigung wurde gespeichert.");
        return "redirect:/admin/bestellbestaetigung";
    }

    /** Renders the exact branded HTML the admin's current draft would produce, for the preview-before-save step. */
    @PostMapping(value = "/preview", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String preview(@RequestParam(defaultValue = "") String htmlBody) {
        return orderMailService.previewHtml(htmlBody);
    }

    /**
     * Sends a made-up order confirmation right now, to diagnose why real ones aren't arriving
     * (see OrderMailService.sendTestMail()) - shows the exact SMTP error on the page rather
     * than just leaving it in the server log, though it's logged there too for later reference.
     */
    @PostMapping("/testmail")
    public String sendTestMail(RedirectAttributes redirectAttributes) {
        try {
            String recipient = orderMailService.sendTestMail();
            redirectAttributes.addFlashAttribute("success", "Test-Mail wurde an " + recipient + " gesendet.");
        } catch (OrderMailService.TestMailException e) {
            redirectAttributes.addFlashAttribute("error",
                    "Test-Mail an " + e.getRecipient() + " fehlgeschlagen: " + e.getMessage()
                            + " - Details stehen im Server-Log.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/bestellbestaetigung";
    }

    private OrderConfirmationForm confirmationFormFromCurrent() {
        OrderConfirmationForm form = new OrderConfirmationForm();
        form.setSubject(orderMailService.getConfirmationSubject());
        form.setHtmlBody(orderMailService.getConfirmationBody());
        return form;
    }
}
