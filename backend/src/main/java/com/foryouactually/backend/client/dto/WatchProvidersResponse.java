package com.foryouactually.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;

/**
 * TMDB's {@code /movie/{id}/watch/providers} response (data by JustWatch): availability keyed by
 * ISO country code, each split into flatrate (subscription), rent and buy lists.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WatchProvidersResponse(Long id, Map<String, RegionProviders> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record RegionProviders(
            String link,
            List<Provider> flatrate,
            List<Provider> rent,
            List<Provider> buy
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Provider(
            int providerId,
            String providerName,
            String logoPath,
            int displayPriority
    ) {
    }
}
