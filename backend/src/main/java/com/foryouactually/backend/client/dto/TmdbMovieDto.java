package com.foryouactually.backend.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * One movie as TMDB returns it. The @JsonNaming annotation maps TMDB's snake_case
 * fields (original_language) onto our camelCase names (originalLanguage) automatically.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbMovieDto(
        Long id,
        String title,
        String originalTitle,
        String originalLanguage,
        String overview,
        String releaseDate,
        String posterPath,
        Double voteAverage,
        Double popularity,
        List<Integer> genreIds
) {
}
