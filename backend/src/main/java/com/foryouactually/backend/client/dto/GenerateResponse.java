package com.foryouactually.backend.client.dto;

import java.util.List;

/**
 * Shape of Gemini's {@code :generateContent} reply. We only care about the first
 * candidate's first text part — the model's written answer.
 */
public record GenerateResponse(List<Candidate> candidates) {

    public record Candidate(Content content) {
    }

    public record Content(List<Part> parts) {
    }

    public record Part(String text) {
    }
}
