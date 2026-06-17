package com.foryouactually.backend.recommend;

import com.foryouactually.backend.error.UnknownUserException;
import com.foryouactually.backend.match.MovieVectorIndex;
import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.UserProfile;
import com.foryouactually.backend.repository.UserProfileRepository;
import com.foryouactually.backend.util.VectorUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
     *
     * <p>{@code extraExclude} carries ids the client has already been shown this session, so
     * paging ("show more") never repeats a film. Results are diversified by original language
     * so a single language can't dominate (the cross-language promise).
     */
    /** Convenience overload with no session-exclusions (used by profile shelves). */
    public List<ScoredMovie> recommend(String userId, Genre genre, int count) {
        return recommend(userId, genre, count, null);
    }

    public List<ScoredMovie> recommend(String userId, Genre genre, int count, Set<Long> extraExclude) {
        UserProfile user = users.findById(userId)
                .orElseThrow(() -> new UnknownUserException(userId));

        Set<Long> exclude = new HashSet<>(user.getSeenMovieIds());
        if (extraExclude != null) {
            exclude.addAll(extraExclude);
        }

        if (genre != null) {
            String dotJson = user.getTasteVectors().get(genre);
            if (dotJson == null) {
                return List.of();
            }
            // Pull a generous candidate pool, then diversify down to `count`.
            List<ScoredMovie> pool =
                    vectorIndex.nearest(VectorUtil.fromJson(dotJson), genre, exclude, poolSize(count));
            return diversifyByLanguage(pool, count);
        }

        return surpriseMe(user, exclude, count);
    }

    /**
     * Round-robin across every seeded dot so each mood contributes (not just the loudest one),
     * then diversify by language. Within a dot we stay in its genre to keep the mood honest.
     */
    private List<ScoredMovie> surpriseMe(UserProfile user, Set<Long> exclude, int count) {
        List<List<ScoredMovie>> pools = new ArrayList<>();
        for (Map.Entry<Genre, String> dot : user.getTasteVectors().entrySet()) {
            float[] vector = VectorUtil.fromJson(dot.getValue());
            List<ScoredMovie> pool = vectorIndex.nearest(vector, dot.getKey(), exclude, poolSize(count));
            if (!pool.isEmpty()) {
                pools.add(pool);
            }
        }
        if (pools.isEmpty()) {
            return List.of();
        }

        // Interleave: take rank-0 from every dot, then rank-1, ... so genres alternate.
        // De-dup by movie id, keeping the first (best-ranked within its own mood) occurrence.
        LinkedHashMap<Long, ScoredMovie> merged = new LinkedHashMap<>();
        int maxLen = pools.stream().mapToInt(List::size).max().orElse(0);
        for (int rank = 0; rank < maxLen; rank++) {
            for (List<ScoredMovie> pool : pools) {
                if (rank < pool.size()) {
                    ScoredMovie candidate = pool.get(rank);
                    merged.putIfAbsent(candidate.movie().getId(), candidate);
                }
            }
        }
        return diversifyByLanguage(new ArrayList<>(merged.values()), count);
    }

    /**
     * Soft per-language cap: at most ~50% of the picks may share one original language. We walk
     * the candidates in their existing (score/round-robin) order, taking each while its language
     * is under cap and deferring the rest. If diversity leaves us short of {@code count}, we
     * back-fill from the deferred ones (still best-scored first) so the user never gets fewer.
     */
    private List<ScoredMovie> diversifyByLanguage(List<ScoredMovie> ranked, int count) {
        if (ranked.size() <= count) {
            return new ArrayList<>(ranked);
        }
        int perLanguageCap = Math.max(1, (int) Math.ceil(count / 2.0));
        Map<String, Integer> langCount = new HashMap<>();
        List<ScoredMovie> chosen = new ArrayList<>(count);
        List<ScoredMovie> deferred = new ArrayList<>();

        for (ScoredMovie candidate : ranked) {
            if (chosen.size() >= count) {
                break;
            }
            String lang = candidate.movie().getOriginalLanguage();
            String key = (lang == null || lang.isBlank()) ? "?" : lang;
            int seen = langCount.getOrDefault(key, 0);
            if (seen < perLanguageCap) {
                chosen.add(candidate);
                langCount.put(key, seen + 1);
            } else {
                deferred.add(candidate);
            }
        }
        for (ScoredMovie candidate : deferred) {
            if (chosen.size() >= count) {
                break;
            }
            chosen.add(candidate);
        }
        return chosen;
    }

    /** A candidate pool a few times larger than the ask, so diversity has room to work. */
    private int poolSize(int count) {
        return Math.max(count * 6, 60);
    }
}
