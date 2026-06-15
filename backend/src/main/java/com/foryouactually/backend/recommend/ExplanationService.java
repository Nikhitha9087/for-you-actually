package com.foryouactually.backend.recommend;

import com.foryouactually.backend.generate.TextGenerationService;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Writes the grounded "why you'll like this" note for a pick.
 *
 * <p>This is the RAG step: we never ask the model to recall a film from memory (it would
 * hallucinate). Instead we hand it the film's real facts <em>and</em> the viewer's own words
 * about what they love, and ask for one honest sentence connecting the two.
 */
@Service
public class ExplanationService {

    private final TextGenerationService generator;

    /** Notes are stable for a given (user, movie, genre), so we cache to save calls and latency. */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public ExplanationService(TextGenerationService generator) {
        this.generator = generator;
    }

    /**
     * Returns a one-line, spoiler-free reason this movie fits the user. Prefers a real generated
     * note; if every provider is exhausted it falls back to a deterministic template built from
     * the film's own facts, so the line is never blank.
     */
    public String explain(UserProfile user, Movie movie, Genre sessionGenre) {
        Genre reasonGenre = pickReasonGenre(user, movie, sessionGenre);
        String seedReason = reasonGenre == null ? null : user.getSeedReasons().get(reasonGenre);

        String key = user.getId() + ":" + movie.getId() + ":" + (reasonGenre == null ? "ANY" : reasonGenre);
        String cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        String note = generator.generate(buildPrompt(movie, seedReason));
        String result = (note != null && !note.isBlank()) ? tidy(note) : templateNote(movie);
        cache.put(key, result);
        return result;
    }

    /**
     * Deterministic, no-LLM fallback: a plain sentence assembled from the film's real metadata.
     * Honest and never empty — used only when all generation providers are unavailable.
     */
    private String templateNote(Movie movie) {
        String lang = languageName(movie.getOriginalLanguage());
        String mood = movie.getGenres().stream().findFirst().map(this::readableGenre).orElse("story");
        StringBuilder s = new StringBuilder("A ");
        if (movie.getReleaseYear() != null) {
            s.append(movie.getReleaseYear()).append(" ");
        }
        s.append(lang).append(" ").append(mood);
        s.append(" that matches the feel of what you told us you love.");
        return s.toString();
    }

    /** Prefer the session genre; otherwise any genre the movie shares with the user's stated tastes. */
    private Genre pickReasonGenre(UserProfile user, Movie movie, Genre sessionGenre) {
        if (sessionGenre != null && user.getSeedReasons().containsKey(sessionGenre)) {
            return sessionGenre;
        }
        if (sessionGenre != null) {
            return sessionGenre;
        }
        for (Genre g : movie.getGenres()) {
            if (user.getSeedReasons().containsKey(g)) {
                return g;
            }
        }
        return movie.getGenres().stream().findFirst().orElse(null);
    }

    private String buildPrompt(Movie movie, String seedReason) {
        String genres = movie.getGenres().stream()
                .map(this::readableGenre)
                .collect(Collectors.joining(", "));

        StringBuilder p = new StringBuilder();
        p.append("You are helping a friend pick what to watch tonight. ")
                .append("Write ONE warm, specific sentence (max 28 words) telling them why this film might land for them.\n\n");

        if (seedReason != null && !seedReason.isBlank()) {
            p.append("In their own words, here's a film they love and why: \"")
                    .append(trim(seedReason, 400)).append("\"\n");
        }

        p.append("The recommended film:\n")
                .append("- Title: ").append(movie.getTitle()).append("\n")
                .append("- Language: ").append(languageName(movie.getOriginalLanguage())).append("\n");
        if (movie.getReleaseYear() != null) {
            p.append("- Year: ").append(movie.getReleaseYear()).append("\n");
        }
        if (genres != null && !genres.isBlank()) {
            p.append("- Mood/genres: ").append(genres).append("\n");
        }
        if (movie.getOverview() != null && !movie.getOverview().isBlank()) {
            p.append("- What it's about: ").append(trim(movie.getOverview(), 700)).append("\n");
        }

        p.append("\nRules:\n")
                .append("- Speak directly to them as \"you\".\n")
                .append("- If it fits, connect it to the feeling they described loving.\n")
                .append("- Use ONLY the facts above. Never invent plot points and never spoil twists or the ending.\n")
                .append("- No preamble, no quotation marks, no film title in quotes. Just the single sentence.");
        return p.toString();
    }

    private String tidy(String note) {
        String n = note.replace("\n", " ").trim();
        if (n.startsWith("\"") && n.endsWith("\"") && n.length() > 1) {
            n = n.substring(1, n.length() - 1).trim();
        }
        return n;
    }

    private String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String readableGenre(Genre g) {
        return switch (g) {
            case THRILLER_MYSTERY -> "thriller/mystery";
            case HORROR -> "horror";
            case ROMANCE_COMEDY -> "romance/comedy";
            case SCIFI_FANTASY -> "sci-fi/fantasy";
            case DRAMA_SLICE_OF_LIFE -> "drama/slice-of-life";
        };
    }

    private String languageName(String code) {
        if (code == null) {
            return "unknown";
        }
        Map<String, String> names = Map.ofEntries(
                Map.entry("en", "English"), Map.entry("ko", "Korean"), Map.entry("ja", "Japanese"),
                Map.entry("es", "Spanish"), Map.entry("fr", "French"), Map.entry("de", "German"),
                Map.entry("hi", "Hindi"), Map.entry("it", "Italian"), Map.entry("zh", "Chinese"),
                Map.entry("pt", "Portuguese"), Map.entry("ta", "Tamil"), Map.entry("te", "Telugu"),
                Map.entry("ml", "Malayalam"), Map.entry("th", "Thai"), Map.entry("sv", "Swedish"),
                Map.entry("da", "Danish")
        );
        return names.getOrDefault(code, code.toUpperCase());
    }
}
