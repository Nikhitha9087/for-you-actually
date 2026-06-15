package com.foryouactually.backend.recommend;

import com.foryouactually.backend.match.MovieVectorIndex;
import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.UserProfile;
import com.foryouactually.backend.repository.UserProfileRepository;
import com.foryouactually.backend.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RecommendationService {

    private final UserProfileRepository users;
    private final MovieVectorIndex vectorIndex;

    public RecommendationService(UserProfileRepository users, MovieVectorIndex vectorIndex) {
        this.users = users;
        this.vectorIndex = vectorIndex;
    }

    /**
     * Returns the next {@code count} picks for a user.
     * With a genre: nearest movies to that genre's taste dot.
     * Without a genre ("surprise me"): the best picks pooled across all the user's dots.
     */
    public List<ScoredMovie> recommend(String userId, Genre genre, int count) {
        UserProfile user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));

        Set<Long> exclude = new HashSet<>(user.getSeenMovieIds());

        if (genre != null) {
            String dotJson = user.getTasteVectors().get(genre);
            if (dotJson == null) {
                return List.of();
            }
            return vectorIndex.nearest(VectorUtil.fromJson(dotJson), genre, exclude, count);
        }

        return surpriseMe(user, exclude, count);
    }

    /** Pull a few from every seeded genre, keep each movie's best score, then take the overall top. */
    private List<ScoredMovie> surpriseMe(UserProfile user, Set<Long> exclude, int count) {
        Map<Long, ScoredMovie> bestPerMovie = new java.util.HashMap<>();
        for (Map.Entry<Genre, String> dot : user.getTasteVectors().entrySet()) {
            float[] vector = VectorUtil.fromJson(dot.getValue());
            for (ScoredMovie candidate : vectorIndex.nearest(vector, dot.getKey(), exclude, count)) {
                ScoredMovie current = bestPerMovie.get(candidate.movie().getId());
                if (current == null || candidate.score() > current.score()) {
                    bestPerMovie.put(candidate.movie().getId(), candidate);
                }
            }
        }
        List<ScoredMovie> pooled = new ArrayList<>(bestPerMovie.values());
        pooled.sort(Comparator.comparingDouble(ScoredMovie::score).reversed());
        return pooled.stream().limit(count).toList();
    }
}
