package com.foryouactually.backend.generate;

/**
 * A pluggable text-generation backend (Groq, Gemini, …). Keeping generation behind this seam
 * means no single provider's quota or outage can take a feature down: the {@link TextGenerationService}
 * walks an ordered list of these and uses the first one that answers.
 */
public interface TextGenerator {

    /** Short label for logs, e.g. "groq" or "gemini". */
    String name();

    /** False when the provider isn't usable (e.g. no API key configured) so the chain skips it. */
    boolean isAvailable();

    /**
     * Produces a written reply for the prompt. Implementations should throw on any failure
     * (rate limit, network, bad response) so the orchestrator can fall through to the next provider.
     */
    String generate(String prompt);
}
