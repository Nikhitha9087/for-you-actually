package com.foryouactually.backend.taste;

import com.foryouactually.backend.client.GeminiClient;
import com.foryouactually.backend.match.MovieVectorIndex;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.model.Reaction;
import com.foryouactually.backend.model.UserProfile;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.repository.UserProfileRepository;
import com.foryouactually.backend.util.VectorMath;
import com.foryouactually.backend.util.VectorUtil;
import com.foryouactually.backend.web.dto.OnboardRequest;
import com.foryouactually.backend.web.dto.ReactRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class TasteProfileService {

    private static final Logger log = LoggerFactory.getLogger(TasteProfileService.class);

    /** How hard a single reaction moves a taste dot (0 = no move, 1 = jump all the way to the movie). */
    private static final double POSITIVE_PULL = 0.30;
    private static final double NEGATIVE_PUSH = -0.30;

    private final GeminiClient gemini;
    private final MovieVectorIndex vectorIndex;
    private final MovieRepository movies;
    private final UserProfileRepository users;

    public TasteProfileService(GeminiClient gemini,
                               MovieVectorIndex vectorIndex,
                               MovieRepository movies,
                               UserProfileRepository users) {
        this.gemini = gemini;
        this.vectorIndex = vectorIndex;
        this.movies = movies;
        this.users = users;
    }

    /**
     * Seeds a new user's taste dots from their favourites + reasons.
     * Each genre's dot starts at the embedding of "title. why" — the user's own words placed on the map.
     */
    public String onboard(OnboardRequest request) {
        UserProfile user = new UserProfile();
        user.setId(UUID.randomUUID().toString());

        if (request.picks() != null) {
            for (OnboardRequest.Pick pick : request.picks()) {
                if (pick.genre() == null || pick.title() == null || pick.title().isBlank()) {
                    continue;
                }
                String why = pick.why() == null ? "" : pick.why();
                // The user's own voice — kept for the "drawn to / avoid" mirror.
                String userWords = (pick.title() + ". " + why).trim();
                float[] vector;
                try {
                    vector = seedVectorFor(pick, userWords);
                } catch (Exception e) {
                    // A pick we can't fingerprint (e.g. live embedding unavailable) shouldn't sink
                    // the whole onboarding — skip it and seed the dots we can.
                    log.warn("Skipping pick '{}' ({}): {}", pick.title(), pick.genre(), e.getMessage());
                    continue;
                }
                user.getTasteVectors().put(pick.genre(), VectorUtil.toJson(vector));
                user.getSeedReasons().put(pick.genre(), userWords);
                // Don't recommend back the exact film they just named.
                if (pick.tmdbId() != null) {
                    user.getSeenMovieIds().add(pick.tmdbId());
                }
            }
        }

        if (user.getTasteVectors().isEmpty()) {
            throw new IllegalStateException(
                    "Could not seed any taste dots — pick films from the suggestions list so we can match them.");
        }

        users.save(user);
        return user.getId();
    }

    /**
     * Places the genre's taste dot. If the picked film is already in our catalogue and
     * fingerprinted, we reuse that vector — zero embedding calls, instant, and quota-free.
     * Otherwise we embed the user's words (enriched with the real synopsis when we have it).
     */
    private float[] seedVectorFor(OnboardRequest.Pick pick, String userWords) {
        if (pick.tmdbId() != null) {
            Movie known = movies.findById(pick.tmdbId()).orElse(null);
            if (known != null && known.getEmbeddingJson() != null) {
                return VectorUtil.fromJson(known.getEmbeddingJson());
            }
        }
        String seedText = pick.overview() == null || pick.overview().isBlank()
                ? userWords
                : pick.title() + ". " + pick.overview() + ". "
                        + (pick.why() == null ? "" : pick.why());
        return gemini.embed(seedText);
    }

    /** Applies a reaction: moves the relevant taste dot(s) and remembers the movie as seen. */
    public void react(ReactRequest request) {
        UserProfile user = users.findById(request.userId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + request.userId()));

        float[] movieVector = vectorIndex.vectorOf(request.movieId());
        if (movieVector != null) {
            double weight = weightFor(request.reaction());
            for (Genre genre : genresToMove(request)) {
                applyNudge(user, genre, movieVector, weight);
            }
        }

        user.getSeenMovieIds().add(request.movieId());
        if (request.reaction() == Reaction.NOT_FOR_ME) {
            user.getDislikedMovieIds().add(request.movieId());
        }
        users.save(user);
    }

    private void applyNudge(UserProfile user, Genre genre, float[] movieVector, double weight) {
        String existing = user.getTasteVectors().get(genre);
        if (existing == null) {
            // No dot here yet: a positive reaction plants one at the movie; a negative one has nothing to push.
            if (weight > 0) {
                user.getTasteVectors().put(genre, VectorUtil.toJson(movieVector));
            }
            return;
        }
        float[] moved = VectorMath.nudge(VectorUtil.fromJson(existing), movieVector, weight);
        user.getTasteVectors().put(genre, VectorUtil.toJson(moved));
    }

    /** If the session has a genre, move that dot; otherwise move every dot the movie belongs to. */
    private Set<Genre> genresToMove(ReactRequest request) {
        if (request.genre() != null) {
            return Set.of(request.genre());
        }
        Movie movie = movies.findById(request.movieId()).orElse(null);
        return movie == null ? Set.of() : movie.getGenres();
    }

    private double weightFor(Reaction reaction) {
        return switch (reaction) {
            case MORE_LIKE_THIS -> POSITIVE_PULL;
            case NOT_FOR_ME -> NEGATIVE_PUSH;
            case SEEN_IT -> 0.0;
        };
    }
}
