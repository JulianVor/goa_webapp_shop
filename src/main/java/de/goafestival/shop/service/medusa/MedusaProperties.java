package de.goafestival.shop.service.medusa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.medusa")
public class MedusaProperties {

    private String storeApiUrl = "http://localhost:9000";
    private String publishableApiKey = "";
    private String defaultCountryCode = "de";
    /**
     * A Medusa "Secret API Key" (Admin Dashboard: Settings > API Key Management > Secret API
     * Keys), needed only for the Abholliste CSV export (see MedusaAdminClient) - unlike the
     * publishable key, this grants admin-level read access. Blank disables that export.
     */
    private String adminSecretApiKey = "";

    public String getStoreApiUrl() {
        return storeApiUrl;
    }

    public void setStoreApiUrl(String storeApiUrl) {
        this.storeApiUrl = storeApiUrl;
    }

    public String getPublishableApiKey() {
        return publishableApiKey;
    }

    public void setPublishableApiKey(String publishableApiKey) {
        this.publishableApiKey = publishableApiKey;
    }

    public String getDefaultCountryCode() {
        return defaultCountryCode;
    }

    public void setDefaultCountryCode(String defaultCountryCode) {
        this.defaultCountryCode = defaultCountryCode;
    }

    public String getAdminSecretApiKey() {
        return adminSecretApiKey;
    }

    public void setAdminSecretApiKey(String adminSecretApiKey) {
        this.adminSecretApiKey = adminSecretApiKey;
    }
}
