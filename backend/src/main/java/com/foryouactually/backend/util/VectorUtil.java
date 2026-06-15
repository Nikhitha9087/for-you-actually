package com.foryouactually.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Converts a vector to/from a JSON string so it can live in a single H2 column.
 * (A real vector database would store these natively — that's a future-work upgrade.)
 */
public final class VectorUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VectorUtil() {
    }

    public static String toJson(float[] vector) {
        try {
            return MAPPER.writeValueAsString(vector);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize vector", e);
        }
    }

    public static float[] fromJson(String json) {
        try {
            return MAPPER.readValue(json, float[].class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize vector", e);
        }
    }
}
