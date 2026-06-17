package com.foryouactually.backend.embed;

import com.foryouactually.backend.client.GeminiClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hosted embeddings via Google Gemini. Active when {@code fya.embedding.provider=gemini}.
 * Kept for parity/fallback; note the free tier caps embeddings at 1,000/day.
 */
@Component
@ConditionalOnProperty(name = "fya.embedding.provider", havingValue = "gemini")
public class GeminiEmbeddingModel implements EmbeddingModel {

    private final GeminiClient gemini;

    public GeminiEmbeddingModel(GeminiClient gemini) {
        this.gemini = gemini;
    }

    @Override
    public float[] embed(String text) {
        return gemini.embed(text);
    }

    @Override
    public List<float[]> batchEmbed(List<String> texts) {
        return gemini.batchEmbed(texts);
    }

    @Override
    public int maxBatch() {
        return GeminiClient.MAX_BATCH;
    }

    @Override
    public String id() {
        return "gemini";
    }
}
