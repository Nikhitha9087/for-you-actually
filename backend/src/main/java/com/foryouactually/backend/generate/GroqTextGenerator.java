package com.foryouactually.backend.generate;

import com.foryouactually.backend.client.dto.GroqChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Primary generator: Groq's hosted Llama (OpenAI-compatible API). Free tier is generous
 * (≈1,000–14,400 req/day depending on model), so this carries the everyday load and the
 * far smaller Gemini generation quota is kept in reserve as a fallback.
 */
@Component
@Order(1)
public class GroqTextGenerator implements TextGenerator {

    private final RestClient http;
    private final String apiKey;
    private final String model;

    public GroqTextGenerator(
            @Value("${fya.groq.base-url}") String baseUrl,
            @Value("${fya.groq.api-key}") String apiKey,
            @Value("${fya.groq.chat-model}") String model) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String name() {
        return "groq";
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String generate(String prompt) {
        int attempt = 0;
        while (true) {
            try {
                return doGenerate(prompt);
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                if (attempt >= 3) {
                    throw e;
                }
                sleep(800L * attempt);
            }
        }
    }

    private String doGenerate(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "max_tokens", 160
        );

        GroqChatResponse response = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .body(GroqChatResponse.class);

        return extractText(response);
    }

    private String extractText(GroqChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            return null;
        }
        GroqChatResponse.Choice choice = response.choices().get(0);
        if (choice.message() == null || choice.message().content() == null) {
            return null;
        }
        return choice.message().content().trim();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
