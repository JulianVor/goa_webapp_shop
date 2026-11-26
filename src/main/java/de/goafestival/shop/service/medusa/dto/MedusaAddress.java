package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mirrors Medusa's AddressPayload exactly (field names confirmed against its Zod
 * validator) - every field is optional there, so this app only actually requires what the
 * checkout form itself marks required, not what Medusa would reject otherwise.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaAddress(
        String first_name,
        String last_name,
        String company,
        String phone,
        String address_1,
        String address_2,
        String city,
        String province,
        String postal_code,
        String country_code) {
}
