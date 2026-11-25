package de.goafestival.shop.service.medusa;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.medusa")
public class MedusaProperties {

    private String storeApiUrl = "http://localhost:9000";
    private String publishableApiKey = "";
    private String defaultCountryCode = "de";

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
}
