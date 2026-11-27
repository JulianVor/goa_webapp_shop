package de.goafestival.shop.service.pricing;

import de.goafestival.shop.domain.PriceObservation;
import de.goafestival.shop.domain.PriceObservationRepository;
import de.goafestival.shop.service.medusa.MedusaStoreClient;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds and reads this shop's own history of prices actually shown for each variant - the
 * only legally usable basis for a "X% off, was Y€" claim (§11 PAngV requires the lowest price
 * actually applied in the last 30 days; Medusa's own Price List "original_amount" is just
 * whatever an admin typed in, not a verified record of prices actually charged - see
 * PriceObservation's javadoc). A genuine future markdown (raise the price for a while, then
 * lower it again) is picked up and shown as a compliant sale automatically, with no admin
 * action beyond changing the Medusa price itself.
 */
@Service
public class PriceHistoryService {

    private static final Logger log = LoggerFactory.getLogger(PriceHistoryService.class);

    private final MedusaStoreClient medusaStoreClient;
    private final PriceObservationRepository repository;

    public PriceHistoryService(MedusaStoreClient medusaStoreClient, PriceObservationRepository repository) {
        this.medusaStoreClient = medusaStoreClient;
        this.repository = repository;
    }

    /**
     * Records today's starting baseline immediately on startup, rather than waiting for the
     * first scheduled run - otherwise a freshly deployed instance would have zero price history
     * for up to a day, and every price would look like a (wrongly) un-trackable brand new one.
     */
    @PostConstruct
    void recordOnStartup() {
        recordObservations();
    }

    @Scheduled(cron = "0 17 3 * * *")
    public void recordObservations() {
        List<MedusaProduct> products;
        try {
            products = medusaStoreClient.listAllProductsForPriceTracking();
        } catch (Exception e) {
            log.warn("Preis-Tracking: Produkte konnten nicht von Medusa geladen werden ({})", e.getMessage());
            return;
        }
        for (MedusaProduct product : products) {
            if (product.variants() == null) {
                continue;
            }
            for (MedusaProduct.Variant variant : product.variants()) {
                MedusaProduct.CalculatedPrice price = variant.calculated_price();
                if (price == null || price.calculated_amount() == null) {
                    continue;
                }
                recordIfChanged(variant.id(), price.calculated_amount(), price.currency_code());
            }
        }
    }

    @Transactional
    void recordIfChanged(String variantId, BigDecimal amount, String currencyCode) {
        Optional<PriceObservation> latest = repository.findTopByVariantIdOrderByObservedAtDesc(variantId);
        if (latest.isPresent() && latest.get().getAmount().compareTo(amount) == 0) {
            return;
        }
        PriceObservation observation = new PriceObservation();
        observation.setVariantId(variantId);
        observation.setAmount(amount);
        observation.setCurrencyCode(currencyCode);
        observation.setObservedAt(Instant.now());
        repository.save(observation);
    }

    /**
     * The lowest price actually recorded for this variant in the last 30 days - or, if it has
     * been tracked for less than 30 days so far, since tracking began (same "since the offer
     * began" exception §11 PAngV itself allows for goods on the market under 30 days). Null if
     * nothing has ever been recorded yet for this variant (e.g. the very first run hasn't
     * reached it, or it has no price at all).
     */
    public BigDecimal lowestPriceLast30Days(String variantId) {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        List<BigDecimal> amounts = new ArrayList<>();
        repository.findTopByVariantIdAndObservedAtBeforeOrderByObservedAtDesc(variantId, cutoff)
                .ifPresent(o -> amounts.add(o.getAmount()));
        repository.findByVariantIdAndObservedAtGreaterThanEqual(variantId, cutoff)
                .forEach(o -> amounts.add(o.getAmount()));
        return amounts.stream().min(BigDecimal::compareTo).orElse(null);
    }

    /** What to actually show for this variant - see PriceDisplay's javadoc. */
    public PriceDisplay resolve(MedusaProduct.Variant variant) {
        MedusaProduct.CalculatedPrice price = variant.calculated_price();
        BigDecimal legalLowest = lowestPriceLast30Days(variant.id());
        return PriceDisplay.of(price.calculated_amount(), price.currency_code(), legalLowest);
    }
}
