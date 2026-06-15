package com.foryouactually.backend.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${fya.tmdb.api-key:}")
    private String tmdbKey;

    @Value("${fya.gemini.api-key:}")
    private String geminiKey;

    @Value("${fya.groq.api-key:}")
    private String groqKey;

    @GetMapping("/health")
    public Map<String, Object> health() {
        String raw = groqKey == null ? "" : groqKey;
        String trimmed = raw.trim();
        return Map.of(
                "status", "up",
                "service", "for-you-actually",
                "tmdbKeyConfigured", !tmdbKey.isBlank(),
                "geminiKeyConfigured", !geminiKey.isBlank(),
                "groqKeyConfigured", !groqKey.isBlank(),
                // Non-secret diagnostics: compare against your known-good key without leaking it.
                "groqKeyRawLen", raw.length(),
                "groqKeyTrimmedLen", trimmed.length(),
                "groqKeyHasWhitespace", raw.length() != trimmed.length(),
                "groqKeyStartsWithGsk", trimmed.startsWith("gsk_")
        );
    }
}
