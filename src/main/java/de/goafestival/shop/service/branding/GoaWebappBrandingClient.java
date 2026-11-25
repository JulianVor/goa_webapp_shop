package de.goafestival.shop.service.branding;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.URI;

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
    private final URI configuredBaseUri;
    /**
     * A Docker Compose container name like "life_webapp-app-1" is a perfectly valid hostname
     * to resolve, but an underscore is never valid in an HTTP Host header - BOTH Spring's
     * default request factory (java.net.http.HttpClient, "unsupported URI") AND, even once
     * that's worked around, the target server's own Tomcat ("The character [_] is never
     * valid in a domain name", HTTP 400) reject it. An IP-literal Host header has no such
     * restriction, so such hosts are resolved and connected to by IP instead.
     *
     * Detected via getHost() == null rather than scanning for "_" directly: java.net.URI
     * silently falls back to "registry-based" authority parsing for a host it can't parse
     * in "server-based" mode (which an underscore triggers) - in that mode getHost()/
     * getPort() are always null, even though getAuthority() still has the raw value.
     */
    private final boolean hostNeedsIpWorkaround;
    private final String rawHost;
    private final int rawPort;
    private volatile Branding current = Branding.fallback();

    public GoaWebappBrandingClient(BrandingProperties properties) {
        this.configuredBaseUri = URI.create(properties.getGoaWebappUrl());
        this.hostNeedsIpWorkaround = configuredBaseUri.getHost() == null;
        if (hostNeedsIpWorkaround) {
            String authority = configuredBaseUri.getAuthority();
            int colon = authority.lastIndexOf(':');
            this.rawHost = authority.substring(0, colon);
            this.rawPort = Integer.parseInt(authority.substring(colon + 1));
        } else {
            this.rawHost = null;
            this.rawPort = -1;
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        // The startup fetch runs on the main thread (see fetchOnStartup) - an unreachable
        // but non-refusing goa_webapp (e.g. a network black hole) must not hang this app's
        // own startup indefinitely.
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(5_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
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
                    .uri(resolveTarget())
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

    private URI resolveTarget() throws Exception {
        if (!hostNeedsIpWorkaround) {
            return configuredBaseUri.resolve("/api/branding");
        }
        // Re-resolved on every call rather than once - a container's IP on its Docker
        // network can change across a restart within the refresh interval.
        String ip = InetAddress.getByName(rawHost).getHostAddress();
        return new URI(configuredBaseUri.getScheme(), null, ip, rawPort, "/api/branding", null, null);
    }
}
