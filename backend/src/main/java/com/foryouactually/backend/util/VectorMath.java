package com.foryouactually.backend.util;

import java.util.List;

/**
 * The handful of vector operations the recommender needs.
 * Everything is "moving points around a map and measuring how close they are."
 */
public final class VectorMath {

    private VectorMath() {
    }

    /**
     * Cosine similarity: how aligned two vectors are, from -1 (opposite) to 1 (identical direction).
     * This is our "closeness on the map of feelings" — higher means more similar in feel.
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * The average position of several vectors — the "centre of gravity" of a set of movies.
     * Used to place a user's taste dot at the middle of the films they liked in a genre.
     */
    public static float[] centroid(List<float[]> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return null;
        }
        int dim = vectors.get(0).length;
        float[] sum = new float[dim];
        for (float[] v : vectors) {
            for (int i = 0; i < dim; i++) {
                sum[i] += v[i];
            }
        }
        for (int i = 0; i < dim; i++) {
            sum[i] /= vectors.size();
        }
        return sum;
    }

    /**
     * Nudge a point toward (positive weight) or away from (negative weight) a target.
     * This is exactly how a 👍 or 👎 moves a user's taste dot.
     */
    public static float[] nudge(float[] point, float[] target, double weight) {
        if (point == null) {
            return target;
        }
        float[] result = new float[point.length];
        for (int i = 0; i < point.length; i++) {
            result[i] = (float) (point[i] + weight * (target[i] - point[i]));
        }
        return result;
    }
}
