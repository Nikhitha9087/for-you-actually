package com.foryouactually.backend.ingest;

import com.foryouactually.backend.model.Genre;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Translates TMDB's ~19 genre ids into our 5 universal buckets.
 * A single movie usually carries several TMDB genres, so the results are unioned:
 * a movie tagged Crime + Science Fiction lands in both THRILLER_MYSTERY and SCIFI_FANTASY.
 */
public final class GenreMapper {

    private static final Map<Integer, Genre> TMDB_TO_BUCKET = Map.ofEntries(
            Map.entry(28, Genre.THRILLER_MYSTERY),      // Action
            Map.entry(80, Genre.THRILLER_MYSTERY),      // Crime
            Map.entry(9648, Genre.THRILLER_MYSTERY),    // Mystery
            Map.entry(53, Genre.THRILLER_MYSTERY),      // Thriller
            Map.entry(27, Genre.HORROR),                // Horror
            Map.entry(35, Genre.ROMANCE_COMEDY),        // Comedy
            Map.entry(10749, Genre.ROMANCE_COMEDY),     // Romance
            Map.entry(12, Genre.SCIFI_FANTASY),         // Adventure
            Map.entry(14, Genre.SCIFI_FANTASY),         // Fantasy
            Map.entry(878, Genre.SCIFI_FANTASY),        // Science Fiction
            Map.entry(18, Genre.DRAMA_SLICE_OF_LIFE),   // Drama
            Map.entry(10751, Genre.DRAMA_SLICE_OF_LIFE),// Family
            Map.entry(36, Genre.DRAMA_SLICE_OF_LIFE),   // History
            Map.entry(10402, Genre.DRAMA_SLICE_OF_LIFE),// Music
            Map.entry(10752, Genre.DRAMA_SLICE_OF_LIFE),// War
            Map.entry(37, Genre.DRAMA_SLICE_OF_LIFE)    // Western
            // Intentionally unmapped: Animation(16), Documentary(99), TV Movie(10770).
            // These describe format, not feel; a movie carrying only these falls back below.
    );

    private GenreMapper() {
    }

    public static Set<Genre> toBuckets(List<Integer> tmdbGenreIds) {
        Set<Genre> buckets = new HashSet<>();
        if (tmdbGenreIds != null) {
            for (Integer id : tmdbGenreIds) {
                Genre bucket = TMDB_TO_BUCKET.get(id);
                if (bucket != null) {
                    buckets.add(bucket);
                }
            }
        }
        if (buckets.isEmpty()) {
            buckets.add(Genre.DRAMA_SLICE_OF_LIFE);
        }
        return buckets;
    }
}
