package de.goafestival.shop.service.pricing;

import de.goafestival.shop.domain.PriceObservation;
import de.goafestival.shop.domain.PriceObservationRepository;
import de.goafestival.shop.service.medusa.MedusaStoreClient;
import de.goafestival.shop.service.medusa.dto.MedusaProduct;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §11 PAngV's whole point is that the "was" price must be something actually charged in the
 * last 30 days - never Medusa's own, admin-typed Price List "original_amount" (see
 * PriceObservation's javadoc). These tests pin down the exact lowest-price-in-30-days
 * calculation that decides whether a sale badge may legally show at all.
 */
class PriceHistoryServiceTest {

    private final MedusaStoreClient medusaStoreClient = mock(MedusaStoreClient.class);
    private final PriceObservationRepository repository = mock(PriceObservationRepository.class);
    private final PriceHistoryService service = new PriceHistoryService(medusaStoreClient, repository);

    @Test
    void noObservationsAtAllMeansNoLegalBasisForASaleClaim() {
        when(repository.findTopByVariantIdAndObservedAtBeforeOrderByObservedAtDesc(eq("variant_1"), any()))
                .thenReturn(Optional.empty());
        when(repository.findByVariantIdAndObservedAtGreaterThanEqual(eq("variant_1"), any()))
                .thenReturn(List.of());

        assertNull(service.lowestPriceLast30Days("variant_1"));

        PriceDisplay display = service.resolve(variant("variant_1", "15.00"));
        assertFalse(display.isOnSale());
        assertEquals(new BigDecimal("15.00"), display.amount());
    }

    @Test
    void aGenuineEarlierHigherPriceOutsideTheWindowIsAValidReference() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        PriceObservation olderHigherPrice = observation("20.00", cutoff.minus(1, ChronoUnit.DAYS));
        when(repository.findTopByVariantIdAndObservedAtBeforeOrderByObservedAtDesc(eq("variant_1"), any()))
                .thenReturn(Optional.of(olderHigherPrice));
        when(repository.findByVariantIdAndObservedAtGreaterThanEqual(eq("variant_1"), any()))
                .thenReturn(List.of());

        PriceDisplay display = service.resolve(variant("variant_1", "15.00"));

        assertTrue(display.isOnSale());
        assertEquals(new BigDecimal("20.00"), display.referenceAmount());
        assertEquals(25, display.discountPercent());
    }

    @Test
    void aPriceThatWasOnlyEverLowerRecentlyIsNotShownAsASale() {
        // The variant was actually cheaper (10.00) within the last 30 days than it is now
        // (15.00) - that's a price INCREASE, never a reduction to advertise.
        PriceObservation recentLowerPrice = observation("10.00", Instant.now().minus(5, ChronoUnit.DAYS));
        when(repository.findTopByVariantIdAndObservedAtBeforeOrderByObservedAtDesc(eq("variant_1"), any()))
                .thenReturn(Optional.empty());
        when(repository.findByVariantIdAndObservedAtGreaterThanEqual(eq("variant_1"), any()))
                .thenReturn(List.of(recentLowerPrice));

        PriceDisplay display = service.resolve(variant("variant_1", "15.00"));

        assertFalse(display.isOnSale());
    }

    @Test
    void recordsAnObservationOnlyWhenThePriceActuallyChanged() {
        PriceObservation existing = observation("15.00", Instant.now());
        when(repository.findTopByVariantIdOrderByObservedAtDesc("variant_1")).thenReturn(Optional.of(existing));

        service.recordIfChanged("variant_1", new BigDecimal("15.00"), "eur");
        verify(repository, never()).save(any());

        service.recordIfChanged("variant_1", new BigDecimal("12.00"), "eur");
        verify(repository).save(any(PriceObservation.class));
    }

    private static PriceObservation observation(String amount, Instant observedAt) {
        PriceObservation observation = new PriceObservation();
        observation.setVariantId("variant_1");
        observation.setAmount(new BigDecimal(amount));
        observation.setCurrencyCode("eur");
        observation.setObservedAt(observedAt);
        return observation;
    }

    private static MedusaProduct.Variant variant(String id, String currentAmount) {
        MedusaProduct.CalculatedPrice price =
                new MedusaProduct.CalculatedPrice(new BigDecimal(currentAmount), null, false, "eur");
        return new MedusaProduct.Variant(id, id, null, price, null, false);
    }
}
