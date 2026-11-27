package de.goafestival.shop.dto;

/** Backing bean for the admin "shop settings" form. */
public class ShopSettingsForm {

    private boolean maintenanceMode;
    private String impressumText;
    private String agbText;
    private String widerrufText;
    private String datenschutzText;
    private String contactEmail;
    private boolean testAccessEnabled;
    private String companyName;
    private String companyStreet;
    private String companyPostalCode;
    private String companyCity;
    private String companyCountry;
    private String returnEmail;
    private String shippingInfoText;
    private String deliveryPaymentNotice;

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

    public boolean isTestAccessEnabled() {
        return testAccessEnabled;
    }

    public void setTestAccessEnabled(boolean testAccessEnabled) {
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
