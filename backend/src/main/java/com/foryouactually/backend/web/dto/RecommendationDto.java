package com.foryouactually.backend.web.dto;

import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;

import java.util.Set;

public record RecommendationDto(
        Long id,
        String title,
        String originalLanguage,
        Integer releaseYear,
        Set<Genre> genres,
        String posterUrl,
        double matchScore,
        String whyYoullLikeIt
) {
    public static RecommendationDto from(ScoredMovie scored) {
        return from(scored, null);
    }

    public static RecommendationDto from(ScoredMovie scored, String whyYoullLikeIt) {
        return new RecommendationDto(
                scored.movie().getId(),
                scored.movie().getTitle(),
                scored.movie().getOriginalLanguage(),
                scored.movie().getReleaseYear(),
                scored.movie().getGenres(),
                scored.movie().getPosterUrl(),
                Math.round(scored.score() * 1000.0) / 1000.0,
                whyYoullLikeIt
        );
    }
}
