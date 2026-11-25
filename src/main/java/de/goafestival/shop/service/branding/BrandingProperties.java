package de.goafestival.shop.service.branding;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.branding")
public class BrandingProperties {

    private String goaWebappUrl = "https://heimfeld.life";
    /** How often to re-fetch after the initial startup fetch, in milliseconds. */
    private long refreshIntervalMs = 300_000;

    public String getGoaWebappUrl() {
        return goaWebappUrl;
    }

    public void setGoaWebappUrl(String goaWebappUrl) {
        this.goaWebappUrl = goaWebappUrl;
    }

    public long getRefreshIntervalMs() {
        return refreshIntervalMs;
    }

    public void setRefreshIntervalMs(long refreshIntervalMs) {
        this.refreshIntervalMs = refreshIntervalMs;
    }
}
