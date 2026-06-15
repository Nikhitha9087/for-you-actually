package com.foryouactually.backend.client.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** One page of TMDB results — TMDB returns 20 movies per page. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record TmdbPageDto(
        int page,
        int totalPages,
        int totalResults,
        List<TmdbMovieDto> results
) {
}
