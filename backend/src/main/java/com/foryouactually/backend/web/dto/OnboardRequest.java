package com.foryouactually.backend.web.dto;

import com.foryouactually.backend.model.Genre;

import java.util.List;

/**
 * First-visit seed: one favourite per genre, plus a short "why".
 * The "why" is the richest signal — it's the user's own words about what they love.
 */
public record OnboardRequest(List<Pick> picks) {

    /**
     * {@code overview} and {@code tmdbId} are filled when the user picked a real film from the
     * TMDB autocomplete. The synopsis makes the seed fingerprint far richer; both are optional
     * so a free-typed title still works.
     */
    public record Pick(Genre genre, String title, String why, String overview, Long tmdbId) {
    }
}
