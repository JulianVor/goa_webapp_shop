package de.goafestival.shop.service.medusa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The Stripe publishable key is safe to expose client-side (it's meant to be embedded in
 * the page for Stripe.js) - the secret key never touches this app, only Medusa
 * (goa_webapp_shop_admin) ever talks to Stripe's API directly.
 */
@ConfigurationProperties(prefix = "app.stripe")
public class StripeProperties {

    private String publishableKey = "";
    /**
     * Medusa's registered payment provider id - "pp_{provider identifier}_{configured id}",
     * see goa_webapp_shop_admin's medusa-config.ts. Only needs changing here if that id
     * ever changes.
     */
    private String providerId = "pp_stripe_stripe";

    public String getPublishableKey() {
        return publishableKey;
    }

    public void setPublishableKey(String publishableKey) {
        this.publishableKey = publishableKey;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
}
