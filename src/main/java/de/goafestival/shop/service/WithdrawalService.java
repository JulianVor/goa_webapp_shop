package de.goafestival.shop.service;

import de.goafestival.shop.domain.WithdrawalRequest;
import de.goafestival.shop.domain.WithdrawalRequestRepository;
import de.goafestival.shop.domain.WithdrawalStatus;
import de.goafestival.shop.service.mail.WithdrawalMailService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Business logic for an electronically declared Widerruf - persisting it, then sending both
 * mails around it (see WithdrawalMailService). Deliberately never touches Medusa: cancelling
 * the order/refunding payment stays a manual step a human does in the Medusa Admin + Stripe
 * Dashboard after seeing the request in this app's own admin worklist (see WithdrawalStatus).
 */
@Service
@Transactional
public class WithdrawalService {

    private final WithdrawalRequestRepository repository;
    private final WithdrawalMailService mailService;
    private final ShopSettingsService shopSettingsService;

    public WithdrawalService(WithdrawalRequestRepository repository, WithdrawalMailService mailService,
                              ShopSettingsService shopSettingsService) {
        this.repository = repository;
        this.mailService = mailService;
        this.shopSettingsService = shopSettingsService;
    }

    public WithdrawalRequest submit(String customerName, String orderReference, String email, String note) {
        WithdrawalRequest request = new WithdrawalRequest();
        request.setCustomerName(customerName);
        request.setOrderReference(orderReference);
        request.setEmail(email);
        request.setNote(note);
        request.setReceivedAt(Instant.now());
        request.setStatus(WithdrawalStatus.NEU);
        request = repository.save(request);
        mailService.sendCustomerConfirmation(request);
        mailService.sendMerchantNotification(request, shopSettingsService.get().getContactEmail());
        return request;
    }

    public List<WithdrawalRequest> listAll() {
        return repository.findAllByOrderByReceivedAtDesc();
    }

    public void setStatus(Long id, WithdrawalStatus status) {
        WithdrawalRequest request = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Widerruf nicht gefunden: " + id));
        request.setStatus(status);
    }
}
