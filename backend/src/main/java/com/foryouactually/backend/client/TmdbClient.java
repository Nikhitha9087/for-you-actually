package com.foryouactually.backend.client;

import com.foryouactually.backend.client.dto.TmdbPageDto;
import com.foryouactually.backend.client.dto.WatchProvidersResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TmdbClient {

    private final RestClient http;
    private final String apiKey;
    private final String imageBaseUrl;

    public TmdbClient(
            @Value("${fya.tmdb.base-url}") String baseUrl,
            @Value("${fya.tmdb.api-key}") String apiKey,
            @Value("${fya.tmdb.image-base-url}") String imageBaseUrl) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.imageBaseUrl = imageBaseUrl;
    }

    /**
     * Pulls one page of well-known movies, ordered by popularity.
     * The vote_count filter keeps out obscure entries with no real audience signal,
     * which keeps catalogue quality high without us hand-curating.
     */
    public TmdbPageDto discoverPopular(int page) {
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/discover/movie")
                        .queryParam("api_key", apiKey)
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("vote_count.gte", 200)
                        .queryParam("include_adult", false)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(TmdbPageDto.class);
    }

    /**
     * Pulls popular movies in a specific original language (e.g. "ko", "ja", "es").
     * This is what gives the catalogue real international coverage instead of a Hollywood skew.
     */
    public TmdbPageDto discoverByLanguage(String originalLanguage, int page, int minVotes) {
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/discover/movie")
                        .queryParam("api_key", apiKey)
                        .queryParam("with_original_language", originalLanguage)
                        .queryParam("sort_by", "popularity.desc")
                        .queryParam("vote_count.gte", minVotes)
                        .queryParam("include_adult", false)
                        .queryParam("page", page)
                        .build())
                .retrieve()
                .body(TmdbPageDto.class);
    }

    /**
     * Free-text title search — powers the onboarding autocomplete so users pick a real film
     * (and we get its real synopsis to seed a much stronger taste fingerprint).
     */
    public TmdbPageDto searchMovies(String query) {
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/search/movie")
                        .queryParam("api_key", apiKey)
                        .queryParam("query", query)
                        .queryParam("include_adult", false)
                        .queryParam("page", 1)
                        .build())
                .retrieve()
                .body(TmdbPageDto.class);
    }

    /**
     * Where-to-watch providers for a film (streaming/rent/buy by country, via JustWatch).
     * Used once at enrichment time so the data can be baked into the catalogue.
     */
    public WatchProvidersResponse watchProviders(long movieId) {
        return http.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/movie/{id}/watch/providers")
                        .queryParam("api_key", apiKey)
                        .build(movieId))
                .retrieve()
                .body(WatchProvidersResponse.class);
    }

    public String toPosterUrl(String posterPath) {
        return posterPath == null ? null : imageBaseUrl + posterPath;
    }

    /** Provider logos are small; w92 is the right size for a chip. */
    public String toLogoUrl(String logoPath) {
        return logoPath == null ? null : "https://image.tmdb.org/t/p/w92" + logoPath;
    }
}
