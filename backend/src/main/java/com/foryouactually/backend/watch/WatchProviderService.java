package com.foryouactually.backend.watch;

import com.foryouactually.backend.client.TmdbClient;
import com.foryouactually.backend.client.dto.WatchProvidersResponse;
import com.foryouactually.backend.client.dto.WatchProvidersResponse.RegionProviders;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.model.WatchInfo;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.util.WatchUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Enriches the catalogue with where-to-watch data from TMDB (JustWatch). Run once with a TMDB
 * key; the result is stored on each movie so the live app surfaces streaming options without any
 * runtime API calls or key. Prefers the user's home region, falling back to the US.
 */
@Service
public class WatchProviderService {

    private static final Logger log = LoggerFactory.getLogger(WatchProviderService.class);

    /** Region preference: try India first, then fall back to the US. */
    private static final List<String> REGION_ORDER = List.of("IN", "US");
    /** Cap the chips so a card stays tidy. */
    private static final int MAX_PROVIDERS = 6;

    private final TmdbClient tmdb;
    private final MovieRepository movies;

    public WatchProviderService(TmdbClient tmdb, MovieRepository movies) {
        this.tmdb = tmdb;
        this.movies = movies;
    }

    /**
     * Fetches providers for up to {@code max} movies that don't have them yet. A failure on one
     * film is logged and skipped (the run continues). Returns done/failed/remaining counts.
     */
    public Result enrichMissing(int max) {
        List<Movie> pending = movies.findAll().stream()
                .filter(m -> m.getWatchJson() == null)
                .limit(max)
                .toList();

        int done = 0;
        int failed = 0;
        for (Movie movie : pending) {
            try {
                WatchInfo info = fetch(movie.getId());
                movie.setWatchJson(WatchUtil.toJson(info));
                movies.save(movie);
                done++;
            } catch (Exception e) {
                failed++;
                log.warn("Watch lookup failed for {} ({}): {}", movie.getId(), movie.getTitle(), e.getMessage());
            }
        }

        long remaining = movies.findAll().stream()
                .filter(m -> m.getWatchJson() == null)
                .count();
        if (done > 0) {
            log.info("Enriched {} movies with watch providers ({} remaining)", done, remaining);
        }
        return new Result(done, failed, remaining);
    }

    private WatchInfo fetch(Long movieId) {
        WatchProvidersResponse resp = tmdb.watchProviders(movieId);
        if (resp == null || resp.results() == null) {
            return empty();
        }
        // Prefer a region that actually has subscription streaming.
        for (String region : REGION_ORDER) {
            RegionProviders rp = resp.results().get(region);
            if (rp != null && rp.flatrate() != null && !rp.flatrate().isEmpty()) {
                return new WatchInfo(region, rp.link(), toProviders(rp.flatrate()));
            }
        }
        // Otherwise keep a link to the rent/buy options if one exists.
        for (String region : REGION_ORDER) {
            RegionProviders rp = resp.results().get(region);
            if (rp != null && rp.link() != null) {
                return new WatchInfo(region, rp.link(), List.of());
            }
        }
        return empty();
    }

    private List<WatchInfo.Provider> toProviders(List<WatchProvidersResponse.Provider> flatrate) {
        return flatrate.stream()
                .sorted(Comparator.comparingInt(WatchProvidersResponse.Provider::displayPriority))
                .limit(MAX_PROVIDERS)
                .map(p -> new WatchInfo.Provider(p.providerName(), tmdb.toLogoUrl(p.logoPath())))
                .toList();
    }

    /** A "looked it up, nothing to show" marker so we never re-fetch this film. */
    private WatchInfo empty() {
        return new WatchInfo(null, null, List.of());
    }

    public record Result(int done, int failed, long remaining) {
    }
}
