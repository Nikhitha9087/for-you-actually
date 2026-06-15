package com.foryouactually.backend.generate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates the generation providers. Spring injects every {@link TextGenerator} in
 * {@code @Order} order (Groq first, Gemini second); we use the first available one that
 * answers. If they all fail we return {@code null}, and the caller falls back to a
 * deterministic, no-LLM template — so a feature never goes blank because of quota or an outage.
 */
@Service
public class TextGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TextGenerationService.class);

    private final List<TextGenerator> generators;

    public TextGenerationService(List<TextGenerator> generators) {
        this.generators = generators;
        log.info("Text generation provider chain: {}",
                generators.stream().map(TextGenerator::name).toList());
    }

    /**
     * Tries each provider in order; returns the first non-blank reply, or {@code null} if
     * every provider is unavailable or fails.
     */
    public String generate(String prompt) {
        for (TextGenerator g : generators) {
            if (!g.isAvailable()) {
                continue;
            }
            try {
                String out = g.generate(prompt);
                if (out != null && !out.isBlank()) {
                    return out;
                }
                log.debug("{} returned empty output; trying next provider", g.name());
            } catch (Exception e) {
                log.warn("{} generation failed ({}); falling through", g.name(), e.getMessage());
            }
        }
        return null;
    }
}
