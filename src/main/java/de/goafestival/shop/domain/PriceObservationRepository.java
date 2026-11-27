package de.goafestival.shop.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

    Optional<PriceObservation> findTopByVariantIdOrderByObservedAtDesc(String variantId);

    /** The single observation representing the price that was active exactly at `cutoff`. */
    Optional<PriceObservation> findTopByVariantIdAndObservedAtBeforeOrderByObservedAtDesc(String variantId, Instant cutoff);

    List<PriceObservation> findByVariantIdAndObservedAtGreaterThanEqual(String variantId, Instant cutoff);
}
