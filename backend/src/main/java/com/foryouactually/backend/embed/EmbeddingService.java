package com.foryouactually.backend.embed;

import com.foryouactually.backend.client.GeminiClient;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.util.VectorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final GeminiClient gemini;
    private final MovieRepository movies;

    public EmbeddingService(GeminiClient gemini, MovieRepository movies) {
        this.gemini = gemini;
        this.movies = movies;
    }

    /**
     * Fingerprints up to {@code max} movies that don't have one yet.
     * Chunked so large catalogues are done over several calls (avoids timeouts and rate-limit bursts).
     * A single failure is logged and skipped rather than aborting the whole batch.
     * Returns how many succeeded, failed, and still remain.
     */
    public Result embedMissing(int max) {
        List<Movie> pending = movies.findAll().stream()
                .filter(m -> m.getEmbeddingJson() == null)
                .limit(max)
                .toList();

        int done = 0;
        int failed = 0;
        for (Movie movie : pending) {
            try {
                float[] vector = gemini.embed(textFor(movie));
                movie.setEmbeddingJson(VectorUtil.toJson(vector));
                movies.save(movie);
                done++;
                if (done % 25 == 0) {
                    log.info("Fingerprinted {}/{} in this batch", done, pending.size());
                }
            } catch (Exception e) {
                failed++;
                log.warn("Fingerprint failed for movie {} ({}): {}",
                        movie.getId(), movie.getTitle(), e.getMessage());
            }
        }

        long remaining = movies.findAll().stream()
                .filter(m -> m.getEmbeddingJson() == null)
                .count();
        return new Result(done, failed, remaining);
    }

    public record Result(int done, int failed, long remaining) {
    }

    /**
     * The text we actually embed. Title gives a little context; the overview carries the feeling.
     * (Later we can enrich this with genre/keywords to sharpen the fingerprint.)
     */
    private String textFor(Movie movie) {
        return movie.getTitle() + ". " + movie.getOverview();
    }
}
