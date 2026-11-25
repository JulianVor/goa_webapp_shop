package de.goafestival.shop.service.branding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors goa_webapp's {@code GET /api/branding} response - the subset of the current
 * edition's/site's look (colors, fonts, background, logo) this shop renders itself in,
 * fetched live instead of frozen into a deploy-time asset sync (see GoaWebappBrandingClient).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Branding(
        String siteTitle,
        String logoImageUrl,
        String faviconImageUrl,
        String backgroundImageUrl,
        String colorPrimary,
        String colorSecondary,
        String colorAccent,
        String colorBox,
        String colorAccentBox,
        String colorHeading,
        String colorButton,
        String colorHeadingOutline,
        String colorText,
        String boxRadius,
        String pillRadius,
        String fontDisplay,
        String fontBody,
        String fontWeightDisplay,
        String fontWeightBody) {

    /**
     * Used until the first successful fetch (or forever, if goa_webapp stays unreachable) -
     * matches style.css's own built-in :root fallback values exactly, so an unreachable
     * goa_webapp degrades to "generic GOA branding" rather than a broken/empty page.
     */
    static Branding fallback() {
        return new Branding(
                "GOA Fanshop", null, null, null,
                "#2f6f68", "#e0559a", "#f2c14e",
                "#2f6f68", "#2f6f68", "#f2c14e", "#f2c14e", "#2f6f68", "#f2c14e",
                "20px", "999px",
                "var(--font-stack-brugty)", "var(--font-stack-poppins)", "normal", "normal");
    }
}
