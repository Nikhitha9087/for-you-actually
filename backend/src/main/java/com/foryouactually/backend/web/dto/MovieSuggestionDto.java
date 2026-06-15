package com.foryouactually.backend.web.dto;

/**
 * A single onboarding autocomplete suggestion, drawn live from TMDB.
 * {@code overview} is carried through so the chosen film's real synopsis can seed the taste dot.
 */
public record MovieSuggestionDto(
        Long tmdbId,
        String title,
        Integer year,
        String language,
        String overview,
        String posterUrl
) {
}
