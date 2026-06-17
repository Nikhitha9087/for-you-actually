package com.foryouactually.backend.embed;

import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.util.VectorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embedder;
    private final MovieRepository movies;

    public EmbeddingService(EmbeddingModel embedder, MovieRepository movies) {
        this.embedder = embedder;
        this.movies = movies;
    }

    /**
     * Wipes every stored fingerprint. Needed when switching embedding models, since vectors
     * from different models live in incompatible spaces and must all be regenerated.
     * Returns how many were cleared.
     */
    public long clearAll() {
        List<Movie> embedded = movies.findAll().stream()
                .filter(m -> m.getEmbeddingJson() != null)
                .toList();
        for (Movie m : embedded) {
            m.setEmbeddingJson(null);
            movies.save(m);
        }
        log.info("Cleared {} embeddings (model switch)", embedded.size());
        return embedded.size();
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
        int batchSize = embedder.maxBatch();
        for (int start = 0; start < pending.size(); start += batchSize) {
            List<Movie> chunk = pending.subList(start, Math.min(start + batchSize, pending.size()));
            try {
                List<String> texts = new ArrayList<>(chunk.size());
                for (Movie m : chunk) {
                    texts.add(textFor(m));
                }
                List<float[]> vectors = embedder.batchEmbed(texts);
                for (int i = 0; i < chunk.size(); i++) {
                    Movie m = chunk.get(i);
                    m.setEmbeddingJson(VectorUtil.toJson(vectors.get(i)));
                    movies.save(m);
                    done++;
                }
                log.info("Fingerprinted batch of {} ({} done this run)", chunk.size(), done);
            } catch (Exception batchError) {
                // One bad text fails the whole batch; retry the chunk per-item so the rest survive.
                log.warn("Batch embed failed ({}); retrying {} movies individually",
                        batchError.getMessage(), chunk.size());
                for (Movie m : chunk) {
                    try {
                        float[] vector = embedder.embed(textFor(m));
                        m.setEmbeddingJson(VectorUtil.toJson(vector));
                        movies.save(m);
                        done++;
                    } catch (Exception e) {
                        failed++;
                        log.warn("Fingerprint failed for movie {} ({}): {}",
                                m.getId(), m.getTitle(), e.getMessage());
                    }
                }
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
