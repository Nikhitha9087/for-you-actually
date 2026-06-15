package com.foryouactually.backend.web.dto;

import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Reaction;

/**
 * A user's reaction to a pick. {@code genre} is the session's genre (which taste dot to move);
 * {@code words} is optional free text ("loved it but too slow") — interpreted by the LLM later.
 */
public record ReactRequest(
        String userId,
        Long movieId,
        Genre genre,
        Reaction reaction,
        String words
) {
}
