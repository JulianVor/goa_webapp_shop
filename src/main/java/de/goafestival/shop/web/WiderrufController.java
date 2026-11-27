package de.goafestival.shop.web;

import de.goafestival.shop.service.WithdrawalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The electronic Widerruf (contract withdrawal, §356a BGB - mandatory since 19.06.2026)
 * customers must be able to declare without writing a letter. Public, no login - see the
 * permanent "Vertrag widerrufen" footer link. Kept entirely separate from the static
 * Widerrufsbelehrung text page (/widerruf).
 */
@Controller
public class WiderrufController {

    private final WithdrawalService withdrawalService;

    public WiderrufController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @ModelAttribute("hasCartCookie")
    public boolean hasCartCookie(@CookieValue(name = CartCookie.NAME, required = false) String cartId) {
        return cartId != null;
    }

    @GetMapping("/widerruf-erklaeren")
    public String form() {
        return "legal/widerruf-erklaeren";
    }

    @PostMapping("/widerruf-erklaeren")
    public String submit(@RequestParam String name,
                          @RequestParam String orderReference,
                          @RequestParam String email,
                          @RequestParam(required = false) String note,
                          RedirectAttributes redirectAttributes) {
        withdrawalService.submit(name, orderReference, email, note);
        redirectAttributes.addFlashAttribute("success",
                "Deine Widerrufserklärung ist eingegangen. Eine Bestätigung haben wir dir per E-Mail geschickt.");
        return "redirect:/widerruf-erklaeren";
    }
}
