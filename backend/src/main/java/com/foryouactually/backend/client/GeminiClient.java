package com.foryouactually.backend.client;

import com.foryouactually.backend.client.dto.EmbedResponse;
import com.foryouactually.backend.client.dto.GenerateResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
public class GeminiClient {

    private final RestClient http;
    private final String apiKey;
    private final String embeddingModel;
    private final String chatModel;

    public GeminiClient(
            @Value("${fya.gemini.base-url}") String baseUrl,
            @Value("${fya.gemini.api-key}") String apiKey,
            @Value("${fya.gemini.embedding-model}") String embeddingModel,
            @Value("${fya.gemini.chat-model}") String chatModel) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
    }

    /**
     * Turns a piece of text into its embedding — a list of numbers placing it on the map of feelings.
     * Two texts with similar meaning/tone come back as nearby vectors.
     */
    public float[] embed(String text) {
        int attempt = 0;
        while (true) {
            try {
                return doEmbed(text);
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                if (attempt >= 5) {
                    throw e;
                }
                // Rate limited: back off a bit longer each time before retrying.
                sleep(1000L * attempt);
            }
        }
    }

    private float[] doEmbed(String text) {
        Map<String, Object> body = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );

        EmbedResponse response = http.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:embedContent")
                        .queryParam("key", apiKey)
                        .build(embeddingModel))
                .body(body)
                .retrieve()
                .body(EmbedResponse.class);

        if (response == null || response.embedding() == null) {
            throw new IllegalStateException("Gemini returned no embedding for text");
        }
        return response.embedding().values();
    }

    /**
     * Free-text generation. Given a prompt, returns the model's written reply.
     * Used for the grounded "why you'll like this" notes. Retries on rate limits.
     */
    public String generate(String prompt) {
        int attempt = 0;
        while (true) {
            try {
                return doGenerate(prompt);
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                if (attempt >= 4) {
                    throw e;
                }
                sleep(800L * attempt);
            }
        }
    }

    private String doGenerate(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", 0.7,
                        "maxOutputTokens", 120
                )
        );

        GenerateResponse response = http.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", apiKey)
                        .build(chatModel))
                .body(body)
                .retrieve()
                .body(GenerateResponse.class);

        return extractText(response);
    }

    private String extractText(GenerateResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }
        GenerateResponse.Candidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null
                || candidate.content().parts().isEmpty()) {
            return null;
        }
        String text = candidate.content().parts().get(0).text();
        return text == null ? null : text.trim();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
