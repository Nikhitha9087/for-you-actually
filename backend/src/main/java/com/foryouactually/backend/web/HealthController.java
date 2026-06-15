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

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "up",
                "service", "for-you-actually",
                "tmdbKeyConfigured", !tmdbKey.isBlank(),
                "geminiKeyConfigured", !geminiKey.isBlank()
        );
    }
}
