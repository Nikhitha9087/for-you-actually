package com.foryouactually.backend.web;

import com.foryouactually.backend.client.TmdbClient;
import com.foryouactually.backend.client.dto.TmdbMovieDto;
import com.foryouactually.backend.client.dto.TmdbPageDto;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.web.dto.MovieSuggestionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Onboarding title autocomplete.
 *
 * <p>When a TMDB key is configured we search all of TMDB, so a user can pick literally any
 * film ever made (its synopsis seeds the taste dot via on-the-fly embedding). When no key is
 * available we fall back to substring search over our own fingerprinted catalogue, so the app
 * still works offline / key-free — every catalogue suggestion seeds a dot with zero embedding
 * calls because its fingerprint is already stored.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private static final int LIMIT = 10;

    private final MovieRepository movies;
    private final TmdbClient tmdb;
    private final boolean tmdbEnabled;

    public SearchController(MovieRepository movies,
                           TmdbClient tmdb,
                           @Value("${fya.tmdb.api-key:}") String tmdbKey) {
        this.movies = movies;
        this.tmdb = tmdb;
        this.tmdbEnabled = tmdbKey != null && !tmdbKey.isBlank();
    }

    @GetMapping("/search")
    public List<MovieSuggestionDto> search(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        if (tmdbEnabled) {
            try {
                List<MovieSuggestionDto> hits = searchTmdb(query.trim());
                if (!hits.isEmpty()) {
                    return hits;
                }
            } catch (Exception e) {
                log.warn("TMDB search failed for '{}' ({}); falling back to catalogue", query, e.getMessage());
            }
        }
        return searchCatalogue(query);
    }

    /**
     * Live, full-TMDB search — any movie ever made. Forgiving by design: we query as typed
     * and also with whitespace collapsed ("old boy" -> "oldboy"), merge both, then re-rank by
     * how well each title matches (exact > prefix > contains > TMDB token match), breaking ties
     * by popularity. That surfaces "Oldboy" at the top even when the user spaces it out.
     */
    private List<MovieSuggestionDto> searchTmdb(String query) {
        Map<Long, TmdbMovieDto> byId = new LinkedHashMap<>();
        collect(byId, query);
        String collapsed = query.replaceAll("\\s+", "");
        if (!collapsed.equalsIgnoreCase(query)) {
            collect(byId, collapsed);
        }
        if (byId.isEmpty()) {
            return List.of();
        }
        String fq = fold(query);
        String fqNoSpace = fq.replaceAll("\\s+", "");
        return byId.values().stream()
                .filter(d -> d.overview() != null && !d.overview().isBlank())
                .sorted(Comparator
                        .comparingInt((TmdbMovieDto d) -> titleTier(fq, fqNoSpace, d.title(), d.originalTitle()))
                        .thenComparing(d -> d.popularity() == null ? 0.0 : d.popularity(),
                                Comparator.reverseOrder()))
                .limit(LIMIT)
                .map(this::toSuggestion)
                .toList();
    }

    /** Runs one TMDB query and folds its hits into the dedup map (first spelling wins on ties). */
    private void collect(Map<Long, TmdbMovieDto> byId, String q) {
        try {
            TmdbPageDto page = tmdb.searchMovies(q);
            if (page != null && page.results() != null) {
                for (TmdbMovieDto d : page.results()) {
                    if (d.id() != null) {
                        byId.putIfAbsent(d.id(), d);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("TMDB search failed for '{}' ({})", q, e.getMessage());
        }
    }

    /** Offline fallback — accent/space-insensitive substring match over the fingerprinted catalogue. */
    private List<MovieSuggestionDto> searchCatalogue(String query) {
        String fq = fold(query);
        String fqNoSpace = fq.replaceAll("\\s+", "");
        return movies.findByEmbeddingJsonIsNotNull().stream()
                .filter(m -> m.getTitle() != null)
                .filter(m -> {
                    String t = fold(m.getTitle());
                    return t.contains(fq) || t.replaceAll("\\s+", "").contains(fqNoSpace);
                })
                .sorted(Comparator
                        .comparingInt((Movie m) -> titleTier(fq, fqNoSpace, m.getTitle(), null))
                        .thenComparing(m -> m.getPopularity() == null ? 0.0 : m.getPopularity(),
                                Comparator.reverseOrder()))
                .limit(LIMIT)
                .map(this::toSuggestion)
                .toList();
    }

    /**
     * Relevance bucket for a candidate title against the query (lower = better):
     * 0 exact, 1 prefix, 2 contains, 3 everything else (matched on individual tokens by TMDB).
     * Both title and original title are checked, in their as-is and space-collapsed forms.
     */
    private int titleTier(String foldedQuery, String foldedQueryNoSpace, String title, String originalTitle) {
        int best = 3;
        best = Math.min(best, tierFor(foldedQuery, foldedQueryNoSpace, title));
        if (originalTitle != null && !originalTitle.isBlank()) {
            best = Math.min(best, tierFor(foldedQuery, foldedQueryNoSpace, originalTitle));
        }
        return best;
    }

    private int tierFor(String foldedQuery, String foldedQueryNoSpace, String candidate) {
        String t = fold(candidate);
        String tNoSpace = t.replaceAll("\\s+", "");
        if (t.equals(foldedQuery) || tNoSpace.equals(foldedQueryNoSpace)) {
            return 0;
        }
        if (t.startsWith(foldedQuery) || tNoSpace.startsWith(foldedQueryNoSpace)) {
            return 1;
        }
        if (t.contains(foldedQuery) || tNoSpace.contains(foldedQueryNoSpace)) {
            return 2;
        }
        return 3;
    }

    /** Lowercase + strip accents, so "amelie" matches "Amélie" and "salo" matches "Salò". */
    private String fold(String s) {
        String normalized = Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private MovieSuggestionDto toSuggestion(Movie m) {
        return new MovieSuggestionDto(
                m.getId(),
                m.getTitle(),
                m.getReleaseYear(),
                m.getOriginalLanguage(),
                m.getOverview(),
                m.getPosterUrl()
        );
    }

    private MovieSuggestionDto toSuggestion(TmdbMovieDto d) {
        return new MovieSuggestionDto(
                d.id(),
                d.title(),
                parseYear(d.releaseDate()),
                d.originalLanguage(),
                d.overview(),
                tmdb.toPosterUrl(d.posterPath())
        );
    }

    private Integer parseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
