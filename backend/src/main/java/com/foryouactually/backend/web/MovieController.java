package com.foryouactually.backend.web;

import com.foryouactually.backend.embed.EmbeddingService;
import com.foryouactually.backend.ingest.IngestionService;
import com.foryouactually.backend.match.MovieVectorIndex;
import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.util.VectorUtil;
import com.foryouactually.backend.web.dto.MovieSummaryDto;

import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MovieController {

    private final IngestionService ingestionService;
    private final EmbeddingService embeddingService;
    private final MovieVectorIndex vectorIndex;
    private final MovieRepository movies;

    public MovieController(IngestionService ingestionService,
                           EmbeddingService embeddingService,
                           MovieVectorIndex vectorIndex,
                           MovieRepository movies) {
        this.ingestionService = ingestionService;
        this.embeddingService = embeddingService;
        this.vectorIndex = vectorIndex;
        this.movies = movies;
    }

    @PostMapping("/admin/ingest")
    public Map<String, Object> ingest(@RequestParam(defaultValue = "1") int pages) {
        int saved = ingestionService.ingest(pages);
        return Map.of(
                "ingestedThisRun", saved,
                "totalInCatalog", movies.count()
        );
    }

    /** Pulls a balanced multi-language catalogue. Call once; re-runs are idempotent. */
    @PostMapping("/admin/ingest/global")
    public Map<String, Object> ingestGlobal(@RequestParam(defaultValue = "5") int pagesPerLanguage,
                                            @RequestParam(defaultValue = "50") int minVotes) {
        List<String> languages = List.of(
                "en", "ja", "ko", "es", "fr", "hi", "zh", "it", "de", "ta", "ru", "pt"
        );
        Map<String, Integer> perLanguage =
                ingestionService.ingestLanguages(languages, pagesPerLanguage, minVotes);
        return Map.of(
                "savedPerLanguage", perLanguage,
                "totalInCatalog", movies.count()
        );
    }

    /** Fingerprints up to {@code max} un-fingerprinted movies. Call repeatedly until remaining = 0. */
    @PostMapping("/admin/embed")
    public Map<String, Object> embed(@RequestParam(defaultValue = "150") int max) {
        EmbeddingService.Result result = embeddingService.embedMissing(max);
        vectorIndex.refresh();
        return Map.of(
                "fingerprintedThisRun", result.done(),
                "failed", result.failed(),
                "remaining", result.remaining(),
                "totalInCatalog", movies.count()
        );
    }

    /**
     * Wipes all stored fingerprints — used when switching embedding models (vectors from
     * different models are not comparable, so the whole catalogue must be re-fingerprinted).
     */
    @PostMapping("/admin/embed/clear")
    public Map<String, Object> clearEmbeddings() {
        long cleared = embeddingService.clearAll();
        vectorIndex.refresh();
        return Map.of("cleared", cleared, "totalInCatalog", movies.count());
    }

    /**
     * Demo of the feel-matching: the movies whose fingerprints are closest to a given movie's.
     * Optional genre filter. This is "what feels like The Godfather?" — the heart of the app.
     */
    @GetMapping("/admin/similar/{id}")
    public List<Map<String, Object>> similar(@org.springframework.web.bind.annotation.PathVariable Long id,
                                              @RequestParam(required = false) Genre genre,
                                              @RequestParam(defaultValue = "5") int limit) {
        float[] target = vectorIndex.vectorOf(id);
        if (target == null) {
            throw new IllegalStateException("Movie " + id + " has no fingerprint yet — run /api/admin/embed first.");
        }
        List<ScoredMovie> results = vectorIndex.nearest(target, genre, Set.of(id), limit);
        return results.stream()
                .map(s -> Map.<String, Object>of(
                        "title", s.movie().getTitle(),
                        "language", s.movie().getOriginalLanguage(),
                        "year", s.movie().getReleaseYear() == null ? 0 : s.movie().getReleaseYear(),
                        "genres", s.movie().getGenres(),
                        "score", Math.round(s.score() * 1000.0) / 1000.0
                ))
                .toList();
    }

    /** Peek at one movie's fingerprint: its size and the first few numbers, just to see it's real. */
    @GetMapping("/admin/fingerprint/{id}")
    public Map<String, Object> fingerprint(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Movie movie = movies.findById(id).orElseThrow();
        if (movie.getEmbeddingJson() == null) {
            return Map.of("title", movie.getTitle(), "hasFingerprint", false);
        }
        float[] vector = VectorUtil.fromJson(movie.getEmbeddingJson());
        float[] preview = new float[Math.min(6, vector.length)];
        System.arraycopy(vector, 0, preview, 0, preview.length);
        return Map.of(
                "title", movie.getTitle(),
                "hasFingerprint", true,
                "dimensions", vector.length,
                "first6", preview
        );
    }

    @GetMapping("/movies")
    public List<MovieSummaryDto> list(@RequestParam(defaultValue = "20") int limit) {
        return movies.findAll().stream()
                .limit(limit)
                .map(MovieSummaryDto::from)
                .toList();
    }
}
