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
import java.util.List;
import java.util.Locale;

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
    private static final int LIMIT = 8;

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

    /** Live, full-TMDB search — any movie ever made. */
    private List<MovieSuggestionDto> searchTmdb(String query) {
        TmdbPageDto page = tmdb.searchMovies(query);
        if (page == null || page.results() == null) {
            return List.of();
        }
        return page.results().stream()
                .filter(d -> d.overview() != null && !d.overview().isBlank())
                .limit(LIMIT)
                .map(this::toSuggestion)
                .toList();
    }

    /** Offline fallback — accent-insensitive substring match over the fingerprinted catalogue. */
    private List<MovieSuggestionDto> searchCatalogue(String query) {
        String needle = fold(query);
        return movies.findByEmbeddingJsonIsNotNull().stream()
                .filter(m -> m.getTitle() != null && fold(m.getTitle()).contains(needle))
                .sorted(Comparator.comparingDouble(
                        (Movie m) -> m.getPopularity() == null ? 0.0 : m.getPopularity()).reversed())
                .limit(LIMIT)
                .map(this::toSuggestion)
                .toList();
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
