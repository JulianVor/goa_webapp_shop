package de.goafestival.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single global row (fixed id) holding this shop's own site-level content - the legal
 * pages a real online shop needs (unlike goa_webapp's plain Impressum) and a
 * maintenance switch. Product/price/inventory data is NOT here - that lives entirely
 * in Medusa (goa_webapp_shop_admin) and is managed through its own Admin Dashboard.
 */
@Entity
@Table(name = "shop_settings")
public class ShopSettings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    /** Shows a "Shop ist bald da" page instead of the storefront while products are still being set up in Medusa. */
    private boolean maintenanceMode = true;

    @Column(columnDefinition = "TEXT")
    private String impressumText;

    @Column(columnDefinition = "TEXT")
    private String agbText;

    @Column(columnDefinition = "TEXT")
    private String widerrufText;

    @Column(columnDefinition = "TEXT")
    private String datenschutzText;

    private String contactEmail;

    private String orderConfirmationSubject;

    @Column(columnDefinition = "TEXT")
    private String orderConfirmationBody;

    /** Whole-shop HTTP Basic gate (see ShopLockFilter), using the admin login's own credentials. Off unless set. */
    private Boolean testAccessEnabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public String getImpressumText() {
        return impressumText;
    }

    public void setImpressumText(String impressumText) {
        this.impressumText = impressumText;
    }

    public String getAgbText() {
        return agbText;
    }

    public void setAgbText(String agbText) {
        this.agbText = agbText;
    }

    public String getWiderrufText() {
        return widerrufText;
    }

    public void setWiderrufText(String widerrufText) {
        this.widerrufText = widerrufText;
    }

    public String getDatenschutzText() {
        return datenschutzText;
    }

    public void setDatenschutzText(String datenschutzText) {
        this.datenschutzText = datenschutzText;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getOrderConfirmationSubject() {
        return orderConfirmationSubject;
    }

    public void setOrderConfirmationSubject(String orderConfirmationSubject) {
        this.orderConfirmationSubject = orderConfirmationSubject;
    }

    public String getOrderConfirmationBody() {
        return orderConfirmationBody;
    }

    public void setOrderConfirmationBody(String orderConfirmationBody) {
        this.orderConfirmationBody = orderConfirmationBody;
    }

    public boolean isTestAccessEnabled() {
        return testAccessEnabled != null && testAccessEnabled;
    }

    public void setTestAccessEnabled(Boolean testAccessEnabled) {
        this.testAccessEnabled = testAccessEnabled;
    }
}
