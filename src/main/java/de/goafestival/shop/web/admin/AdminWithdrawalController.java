package de.goafestival.shop.web.admin;

import de.goafestival.shop.domain.WithdrawalStatus;
import de.goafestival.shop.service.WithdrawalService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Manual worklist for received Widerrufe - lists them and lets an admin flip the status once
 * handled. Deliberately no automatic Medusa order cancellation/refund here (see WithdrawalService).
 */
@Controller
@RequestMapping("/admin/widerrufe")
public class AdminWithdrawalController {

    private final WithdrawalService withdrawalService;

    public AdminWithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("withdrawals", withdrawalService.listAll());
        return "admin/widerrufe";
    }

    @PostMapping("/{id}/status")
    public String setStatus(@PathVariable Long id, @RequestParam WithdrawalStatus status,
                             RedirectAttributes redirectAttributes) {
        withdrawalService.setStatus(id, status);
        redirectAttributes.addFlashAttribute("success", "Status aktualisiert.");
        return "redirect:/admin/widerrufe";
    }
}
