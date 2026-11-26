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
}
