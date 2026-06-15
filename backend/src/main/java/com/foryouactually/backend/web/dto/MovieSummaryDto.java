package com.foryouactually.backend.web.dto;

import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;

import java.util.Set;

public record MovieSummaryDto(
        Long id,
        String title,
        String originalLanguage,
        Integer releaseYear,
        Double voteAverage,
        Set<Genre> genres,
        String posterUrl,
        boolean hasFingerprint
) {
    public static MovieSummaryDto from(Movie m) {
        return new MovieSummaryDto(
                m.getId(),
                m.getTitle(),
                m.getOriginalLanguage(),
                m.getReleaseYear(),
                m.getVoteAverage(),
                m.getGenres(),
                m.getPosterUrl(),
                m.getEmbeddingJson() != null
        );
    }
}
