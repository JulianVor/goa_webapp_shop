package de.goafestival.shop.service.branding;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Fetches goa_webapp's current colors/fonts/background/logo at startup and periodically
 * afterwards, caching the last successful result in memory - this shop has no database
 * access to goa_webapp (deliberately, see the shop concept), so this live call is how it
 * stays visually in sync with whatever edition is currently active there, instead of
 * depending on someone remembering to re-run a deploy-time sync script after every
 * branding change.
 *
 * Deliberately tolerant of goa_webapp being briefly unreachable: a failed fetch just logs
 * a warning and keeps serving the last known-good value (or the built-in fallback, before
 * the first successful fetch) rather than breaking the storefront.
 */
@Service
@EnableConfigurationProperties(BrandingProperties.class)
public class GoaWebappBrandingClient {

    private static final Logger log = LoggerFactory.getLogger(GoaWebappBrandingClient.class);

    private final RestClient restClient;
    private volatile Branding current = Branding.fallback();

    public GoaWebappBrandingClient(BrandingProperties properties) {
        this.restClient = RestClient.builder()
                .baseUrl(properties.getGoaWebappUrl())
                .build();
    }

    public Branding current() {
        return current;
    }

    @PostConstruct
    void fetchOnStartup() {
        refresh();
    }

    @Scheduled(fixedRateString = "${app.branding.refresh-interval-ms:300000}")
    void refresh() {
        try {
            Branding fetched = restClient.get()
                    .uri("/api/branding")
                    .retrieve()
                    .body(Branding.class);
            if (fetched != null) {
                current = fetched;
            }
        } catch (Exception e) {
            log.warn("Could not refresh branding from goa_webapp ({}) - keeping last known branding.",
                    e.getMessage());
        }
    }
}
