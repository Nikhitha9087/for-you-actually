package com.foryouactually.backend.web.dto;

import com.foryouactually.backend.model.Genre;

import java.util.List;

/**
 * The taste-profile page payload: a plain-English mirror of the user's taste
 * plus one cross-language shelf per genre they've seeded.
 */
public record TasteProfileDto(
        List<String> drawnTo,
        List<String> avoid,
        List<Shelf> shelves
) {
    public record Shelf(Genre genre, List<RecommendationDto> items) {
    }
}
