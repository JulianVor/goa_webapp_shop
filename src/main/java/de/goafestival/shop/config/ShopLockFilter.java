package de.goafestival.shop.config;

import de.goafestival.shop.service.ShopSettingsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Optional whole-shop HTTP Basic gate, turned on/off from the admin panel
 * (ShopSettings.testAccessEnabled) rather than at deploy time - same pattern as goa_webapp's
 * SiteLockFilter. Runs before Spring Security's own filters, so it applies to literally every
 * request - including /admin and /login - completely independent of the admin login underneath
 * it, even though it reuses that same login's credentials. No-op while disabled.
 *
 * Unlike goa_webapp, this app doesn't itself serve any path another, unauthenticated party
 * needs to reach while the lock is on (no uploads of its own, no public API another service
 * calls into) - so unlike SiteLockFilter, there's nothing to exempt.
 */
public class ShopLockFilter extends OncePerRequestFilter {

    private final String username;
    private final String password;
    private final ShopSettingsService shopSettingsService;

    public ShopLockFilter(String username, String password, ShopSettingsService shopSettingsService) {
        this.username = username;
        this.password = password;
        this.shopSettingsService = shopSettingsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!shopSettingsService.get().isTestAccessEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6)), StandardCharsets.UTF_8);
            int colon = decoded.indexOf(':');
            if (colon >= 0 && decoded.substring(0, colon).equals(username) && decoded.substring(colon + 1).equals(password)) {
                filterChain.doFilter(request, response);
                return;
            }
        }
        response.setHeader("WWW-Authenticate", "Basic realm=\"Testbetrieb\"");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
