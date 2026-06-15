package com.foryouactually.backend.recommend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foryouactually.backend.generate.TextGenerationService;
import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.model.UserProfile;
import com.foryouactually.backend.repository.MovieRepository;
import com.foryouactually.backend.repository.UserProfileRepository;
import com.foryouactually.backend.web.dto.RecommendationDto;
import com.foryouactually.backend.web.dto.TasteProfileDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the taste-profile page: a plain-English "mirror" of what the user is drawn to /
 * avoids, plus a cross-language shelf per genre. The mirror is the reflective payoff of the
 * whole app — it tells people something true about themselves, in their own emotional language.
 */
@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    /** How many films per genre shelf. */
    private static final int SHELF_SIZE = 8;
    /** How many liked films to feed the mirror (across all genres). */
    private static final int MIRROR_SAMPLE = 12;

    private final UserProfileRepository users;
    private final MovieRepository movies;
    private final RecommendationService recommendations;
    private final TextGenerationService generator;
    private final ObjectMapper json = new ObjectMapper();

    public ProfileService(UserProfileRepository users,
                          MovieRepository movies,
                          RecommendationService recommendations,
                          TextGenerationService generator) {
        this.users = users;
        this.movies = movies;
        this.recommendations = recommendations;
        this.generator = generator;
    }

    public TasteProfileDto build(String userId) {
        UserProfile user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));

        List<TasteProfileDto.Shelf> shelves = new ArrayList<>();
        Map<Long, Movie> likedSample = new LinkedHashMap<>();

        // Genres in their natural order, but only the ones this user actually seeded.
        for (Genre genre : Genre.values()) {
            if (!user.getTasteVectors().containsKey(genre)) {
                continue;
            }
            List<ScoredMovie> picks = recommendations.recommend(userId, genre, SHELF_SIZE);
            if (picks.isEmpty()) {
                continue;
            }
            shelves.add(new TasteProfileDto.Shelf(
                    genre,
                    picks.stream().map(RecommendationDto::from).toList()
            ));
            for (ScoredMovie p : picks) {
                if (likedSample.size() < MIRROR_SAMPLE) {
                    likedSample.putIfAbsent(p.movie().getId(), p.movie());
                }
            }
        }

        TasteMirror mirror = buildMirror(user, new ArrayList<>(likedSample.values()));
        return new TasteProfileDto(mirror.drawnTo(), mirror.avoid(), shelves);
    }

    private TasteMirror buildMirror(UserProfile user, List<Movie> liked) {
        List<Movie> disliked = movies.findAllById(user.getDislikedMovieIds());
        try {
            String raw = generator.generate(buildMirrorPrompt(user, liked, disliked));
            TasteMirror parsed = parse(raw);
            if (parsed != null && !parsed.drawnTo().isEmpty()) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("Could not build taste mirror for {}: {}", user.getId(), e.getMessage());
        }
        // No-LLM fallback: name the genres they seeded so the mirror is never empty.
        return templateMirror(user);
    }

    /**
     * Deterministic fallback mirror derived from the genres the user actually seeded — used only
     * when generation is unavailable. Coarser than the LLM phrasing, but honest and never blank.
     */
    private TasteMirror templateMirror(UserProfile user) {
        List<String> drawnTo = user.getTasteVectors().keySet().stream()
                .map(this::genrePhrase)
                .toList();
        return new TasteMirror(drawnTo, List.of());
    }

    private String genrePhrase(Genre g) {
        return switch (g) {
            case THRILLER_MYSTERY -> "tension and mystery";
            case HORROR -> "dread and the uncanny";
            case ROMANCE_COMEDY -> "warmth and wit";
            case SCIFI_FANTASY -> "wonder and big ideas";
            case DRAMA_SLICE_OF_LIFE -> "quiet, character-driven moments";
        };
    }

    private String buildMirrorPrompt(UserProfile user, List<Movie> liked, List<Movie> disliked) {
        StringBuilder p = new StringBuilder();
        p.append("You are reflecting a film lover's taste back to them in vivid, plain language.\n\n");

        if (!user.getSeedReasons().isEmpty()) {
            p.append("In their own words, films they love and why:\n");
            user.getSeedReasons().values().forEach(r -> p.append("- ").append(trim(r, 300)).append("\n"));
            p.append("\n");
        }

        p.append("Films they gravitate toward (title — what it's about):\n");
        for (Movie m : liked) {
            p.append("- ").append(m.getTitle()).append(" — ").append(trim(overview(m), 240)).append("\n");
        }

        if (!disliked.isEmpty()) {
            p.append("\nFilms they pushed away (\"not for me\"):\n");
            for (Movie m : disliked) {
                p.append("- ").append(m.getTitle()).append(" — ").append(trim(overview(m), 200)).append("\n");
            }
        }

        p.append("\nReturn ONLY strict JSON (no markdown, no commentary) in exactly this shape:\n")
                .append("{\"drawnTo\": [\"...\"], \"avoid\": [\"...\"]}\n\n")
                .append("Rules:\n")
                .append("- drawnTo: 3 to 6 short phrases naming recurring feelings, themes or textures ")
                .append("(e.g. \"slow-building dread\", \"unreliable narrators\", \"quiet melancholy\"). Lowercase unless a proper noun.\n")
                .append("- avoid: 2 to 4 short phrases for what they likely steer clear of, inferred from the contrast ")
                .append("with what they love and anything they pushed away.\n")
                .append("- Phrases only. No sentences, no explanations.");
        return p.toString();
    }

    private TasteMirror parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)\\s*```$", "").trim();
        }
        try {
            JsonNode node = json.readTree(cleaned);
            return new TasteMirror(strings(node.get("drawnTo")), strings(node.get("avoid")));
        } catch (Exception e) {
            log.warn("Mirror JSON parse failed: {}", e.getMessage());
            return null;
        }
    }

    private List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(n -> {
                String v = n.asText("").trim();
                if (!v.isBlank()) {
                    out.add(v);
                }
            });
        }
        return out;
    }

    private String overview(Movie m) {
        return m.getOverview() == null ? "" : m.getOverview();
    }

    private String trim(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private record TasteMirror(List<String> drawnTo, List<String> avoid) {
    }
}
