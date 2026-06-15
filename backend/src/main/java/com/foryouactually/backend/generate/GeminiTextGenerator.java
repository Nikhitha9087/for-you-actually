package com.foryouactually.backend.generate;

import com.foryouactually.backend.client.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fallback generator: Gemini. Used only when Groq is unavailable or fails, so its small
 * free-tier generation quota is spent sparingly. Embeddings still go through {@link GeminiClient}
 * directly elsewhere — this seam is only about text generation.
 */
@Component
@Order(2)
public class GeminiTextGenerator implements TextGenerator {

    private final GeminiClient gemini;
    private final String apiKey;

    public GeminiTextGenerator(GeminiClient gemini,
                               @Value("${fya.gemini.api-key}") String apiKey) {
        this.gemini = gemini;
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String generate(String prompt) {
        return gemini.generate(prompt);
    }
}
