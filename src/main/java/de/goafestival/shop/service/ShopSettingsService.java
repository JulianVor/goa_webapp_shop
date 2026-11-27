package de.goafestival.shop.service;

import de.goafestival.shop.domain.ShopSettings;
import de.goafestival.shop.domain.ShopSettingsRepository;
import de.goafestival.shop.dto.ShopSettingsForm;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Objects;

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
        if (!Objects.equals(settings.getAgbText(), form.getAgbText())) {
            settings.setAgbVersion(settings.getAgbVersion() + 1);
            settings.setAgbVersionDate(LocalDate.now());
        }
        if (!Objects.equals(settings.getWiderrufText(), form.getWiderrufText())) {
            settings.setWiderrufVersion(settings.getWiderrufVersion() + 1);
            settings.setWiderrufVersionDate(LocalDate.now());
        }
        settings.setMaintenanceMode(form.isMaintenanceMode());
        settings.setImpressumText(form.getImpressumText());
        settings.setAgbText(form.getAgbText());
        settings.setWiderrufText(form.getWiderrufText());
        settings.setDatenschutzText(form.getDatenschutzText());
        settings.setContactEmail(form.getContactEmail());
        settings.setTestAccessEnabled(form.isTestAccessEnabled());
        settings.setCompanyName(form.getCompanyName());
        settings.setCompanyStreet(form.getCompanyStreet());
        settings.setCompanyPostalCode(form.getCompanyPostalCode());
        settings.setCompanyCity(form.getCompanyCity());
        settings.setCompanyCountry(form.getCompanyCountry());
        settings.setReturnEmail(form.getReturnEmail());
        settings.setShippingInfoText(form.getShippingInfoText());
        settings.setDeliveryPaymentNotice(form.getDeliveryPaymentNotice());
        shopSettingsRepository.save(settings);
    }

    public void updateOrderConfirmationTemplate(String subject, String body) {
        ShopSettings settings = get();
        settings.setOrderConfirmationSubject(subject);
        settings.setOrderConfirmationBody(body);
        shopSettingsRepository.save(settings);
    }
}
