package de.goafestival.shop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

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

    /**
     * Structured seller/"Verkäufer" data - needed verbatim (not just buried in the free-text
     * Impressum) for the Muster-Widerrufsformular and the order confirmation mail's seller info.
     */
    private String companyName;
    private String companyStreet;
    private String companyPostalCode;
    private String companyCity;
    private String companyCountry = "Deutschland";
    /** Address returned goods go to - may differ from contactEmail/the Impressum address. */
    private String returnEmail;

    /**
     * Bumped automatically (see ShopSettingsService.update) whenever the admin saves a changed
     * agbText/widerrufText - embedded in every order confirmation mail so it's traceable which
     * fassung applied at order time, without keeping a full version history.
     */
    private int agbVersion = 1;
    private LocalDate agbVersionDate;
    private int widerrufVersion = 1;
    private LocalDate widerrufVersionDate;

    @Column(columnDefinition = "TEXT")
    private String shippingInfoText;

    @Column(columnDefinition = "TEXT")
    private String deliveryPaymentNotice;

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

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getCompanyStreet() {
        return companyStreet;
    }

    public void setCompanyStreet(String companyStreet) {
        this.companyStreet = companyStreet;
    }

    public String getCompanyPostalCode() {
        return companyPostalCode;
    }

    public void setCompanyPostalCode(String companyPostalCode) {
        this.companyPostalCode = companyPostalCode;
    }

    public String getCompanyCity() {
        return companyCity;
    }

    public void setCompanyCity(String companyCity) {
        this.companyCity = companyCity;
    }

    public String getCompanyCountry() {
        return companyCountry;
    }

    public void setCompanyCountry(String companyCountry) {
        this.companyCountry = companyCountry;
    }

    public String getReturnEmail() {
        return returnEmail;
    }

    public void setReturnEmail(String returnEmail) {
        this.returnEmail = returnEmail;
    }

    public int getAgbVersion() {
        return agbVersion;
    }

    public void setAgbVersion(int agbVersion) {
        this.agbVersion = agbVersion;
    }

    public LocalDate getAgbVersionDate() {
        return agbVersionDate;
    }

    public void setAgbVersionDate(LocalDate agbVersionDate) {
        this.agbVersionDate = agbVersionDate;
    }

    public int getWiderrufVersion() {
        return widerrufVersion;
    }

    public void setWiderrufVersion(int widerrufVersion) {
        this.widerrufVersion = widerrufVersion;
    }

    public LocalDate getWiderrufVersionDate() {
        return widerrufVersionDate;
    }

    public void setWiderrufVersionDate(LocalDate widerrufVersionDate) {
        this.widerrufVersionDate = widerrufVersionDate;
    }

    public String getShippingInfoText() {
        return shippingInfoText;
    }

    public void setShippingInfoText(String shippingInfoText) {
        this.shippingInfoText = shippingInfoText;
    }

    public String getDeliveryPaymentNotice() {
        return deliveryPaymentNotice;
    }

    public void setDeliveryPaymentNotice(String deliveryPaymentNotice) {
        this.deliveryPaymentNotice = deliveryPaymentNotice;
    }
}
