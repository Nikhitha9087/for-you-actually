package com.foryouactually.backend.match;

import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.util.VectorMath;
import com.foryouactually.backend.util.VectorUtil;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps every movie's fingerprint in memory so "find the nearest movies to this point"
 * is instant. Rebuilt whenever new fingerprints are generated.
 */
@Component
public class MovieVectorIndex {

    private final MovieRepository movies;

    private volatile List<Movie> indexed = List.of();
    private volatile Map<Long, float[]> vectors = Map.of();

    public MovieVectorIndex(MovieRepository movies) {
        this.movies = movies;
    }

    public synchronized void refresh() {
        List<Movie> withFingerprint = movies.findAll().stream()
                .filter(m -> m.getEmbeddingJson() != null)
                .toList();
        Map<Long, float[]> map = new HashMap<>();
        for (Movie m : withFingerprint) {
            map.put(m.getId(), VectorUtil.fromJson(m.getEmbeddingJson()));
        }
        this.indexed = withFingerprint;
        this.vectors = map;
    }

    private void ensureLoaded() {
        if (vectors.isEmpty()) {
            refresh();
        }
    }

    public float[] vectorOf(Long movieId) {
        ensureLoaded();
        return vectors.get(movieId);
    }

    /**
     * The core operation: the movies whose fingerprints are closest to {@code target}.
     * Optionally restricted to one genre and excluding ids the user has already seen.
     */
    public List<ScoredMovie> nearest(float[] target, Genre genre, Set<Long> exclude, int limit) {
        return nearest(target, genre, null, exclude, limit);
    }

    /**
     * As {@link #nearest(float[], Genre, Set, int)} but also restrictable to one original
     * language (e.g. "ko"). The language filter runs before the limit, so the result is the
     * best matches <em>within</em> that language rather than the global best then filtered.
     */
    public List<ScoredMovie> nearest(float[] target, Genre genre, String language, Set<Long> exclude, int limit) {
        ensureLoaded();
        if (target == null) {
            return List.of();
        }
        return indexed.stream()
                .filter(m -> genre == null || m.getGenres().contains(genre))
                .filter(m -> language == null || language.equalsIgnoreCase(m.getOriginalLanguage()))
                .filter(m -> exclude == null || !exclude.contains(m.getId()))
                .map(m -> new ScoredMovie(m, VectorMath.cosine(target, vectors.get(m.getId()))))
                .sorted(Comparator.comparingDouble(ScoredMovie::score).reversed())
                .limit(limit)
                .toList();
    }

    public int size() {
        ensureLoaded();
        return indexed.size();
    }
}
