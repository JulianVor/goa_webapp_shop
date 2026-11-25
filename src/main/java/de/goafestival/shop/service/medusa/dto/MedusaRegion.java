package de.goafestival.shop.service.medusa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedusaRegion(String id, String name, String currency_code, List<Country> countries) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Country(String iso_2) {
    }

    public boolean coversCountry(String countryCode) {
        if (countries == null || countryCode == null) {
            return false;
        }
        return countries.stream().anyMatch(c -> countryCode.equalsIgnoreCase(c.iso_2()));
    }
}
