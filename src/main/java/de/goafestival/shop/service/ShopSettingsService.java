package de.goafestival.shop.service;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.domain.ShopSettingsRepository;
import de.goafestival.shop.dto.ShopSettingsForm;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class ShopSettingsService {

    private final ShopSettingsRepository shopSettingsRepository;

    public ShopSettingsService(ShopSettingsRepository shopSettingsRepository) {
        this.shopSettingsRepository = shopSettingsRepository;
    }

    /** Returns the single global settings row, creating it (maintenance mode on) on first access. */
    public ShopSettings get() {
        return shopSettingsRepository.findById(ShopSettings.SINGLETON_ID)
                .orElseGet(() -> shopSettingsRepository.save(new ShopSettings()));
    }

    public void update(ShopSettingsForm form) {
        ShopSettings settings = get();
        settings.setMaintenanceMode(form.isMaintenanceMode());
        settings.setImpressumText(form.getImpressumText());
        settings.setAgbText(form.getAgbText());
        settings.setWiderrufText(form.getWiderrufText());
        settings.setDatenschutzText(form.getDatenschutzText());
        settings.setContactEmail(form.getContactEmail());
        shopSettingsRepository.save(settings);
    }

    public void updateOrderConfirmationTemplate(String subject, String body) {
        ShopSettings settings = get();
        settings.setOrderConfirmationSubject(subject);
        settings.setOrderConfirmationBody(body);
        shopSettingsRepository.save(settings);
    }
}
