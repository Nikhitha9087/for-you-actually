package com.foryouactually.backend.client.dto;

/**
 * Gemini's response to an embedding request: a single vector of numbers
 * (the movie's coordinates on the "map of feelings"). text-embedding-004 returns 768 numbers.
 */
public record EmbedResponse(Embedding embedding) {
    public record Embedding(float[] values) {
    }
}
