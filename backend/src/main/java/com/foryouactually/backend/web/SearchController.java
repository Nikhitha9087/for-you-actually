package com.foryouactually.backend.web;

import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.web.dto.MovieSuggestionDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Onboarding title autocomplete. Searches our own fingerprinted catalogue so that every
 * suggestion can seed a taste dot instantly, with zero embedding calls (quota-free) — the
 * picked film's stored fingerprint is reused directly.
 */
@RestController
@RequestMapping("/api")
public class SearchController {

    private final MovieRepository movies;

    public SearchController(MovieRepository movies) {
        this.movies = movies;
    }

    @GetMapping("/search")
    public List<MovieSuggestionDto> search(@RequestParam("q") String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        String needle = fold(query);
        return movies.findByEmbeddingJsonIsNotNull().stream()
                .filter(m -> m.getTitle() != null && fold(m.getTitle()).contains(needle))
                .sorted(Comparator.comparingDouble(
                        (Movie m) -> m.getPopularity() == null ? 0.0 : m.getPopularity()).reversed())
                .limit(8)
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
}
