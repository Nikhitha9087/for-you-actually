package com.foryouactually.backend.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Shape of Groq's OpenAI-compatible {@code /chat/completions} reply. We only read the first
 * choice's message content — the model's written answer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {
    }
}
