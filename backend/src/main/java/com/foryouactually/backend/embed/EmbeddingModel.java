package com.foryouactually.backend.embed;

import java.util.List;

/**
 * The "feeling fingerprint" generator: turns text into a vector on the map of feelings.
 *
 * <p>Pluggable so the catalogue can be fingerprinted either by a hosted API (Gemini) or a
 * fully local, quota-free model (DJL + sentence-transformers). All vectors in the system must
 * come from the <em>same</em> implementation — embeddings from different models live in
 * different spaces and cannot be compared.
 */
public interface EmbeddingModel {

    /** Fingerprints a single piece of text. */
    float[] embed(String text);

    /** Fingerprints many texts at once (order preserved). Never larger than {@link #maxBatch()}. */
    List<float[]> batchEmbed(List<String> texts);

    /** Largest batch this model accepts in one call. */
    int maxBatch();

    /** Short identifier for logs/diagnostics, e.g. "gemini" or "local:all-MiniLM-L6-v2". */
    String id();
}
