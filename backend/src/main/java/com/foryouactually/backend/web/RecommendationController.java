package com.foryouactually.backend.web;

import com.foryouactually.backend.match.ScoredMovie;
import com.foryouactually.backend.model.Genre;
import com.foryouactually.backend.model.UserProfile;
import com.foryouactually.backend.recommend.ExplanationService;
import com.foryouactually.backend.recommend.RecommendationService;
import com.foryouactually.backend.repository.UserProfileRepository;
import com.foryouactually.backend.taste.TasteProfileService;
import com.foryouactually.backend.web.dto.OnboardRequest;
import com.foryouactually.backend.web.dto.ReactRequest;
import com.foryouactually.backend.web.dto.RecommendationDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class RecommendationController {

    private final TasteProfileService tasteProfile;
    private final RecommendationService recommendations;
    private final ExplanationService explanations;
    private final UserProfileRepository users;

    public RecommendationController(TasteProfileService tasteProfile,
                                    RecommendationService recommendations,
                                    ExplanationService explanations,
                                    UserProfileRepository users) {
        this.tasteProfile = tasteProfile;
        this.recommendations = recommendations;
        this.explanations = explanations;
        this.users = users;
    }

    @PostMapping("/onboard")
    public Map<String, Object> onboard(@RequestBody OnboardRequest request) {
        String userId = tasteProfile.onboard(request);
        return Map.of("userId", userId);
    }

    @GetMapping("/recommend")
    public List<RecommendationDto> recommend(@RequestParam String userId,
                                             @RequestParam(required = false) Genre genre,
                                             @RequestParam(defaultValue = "9") int count,
                                             @RequestParam(required = false) String exclude) {
        List<ScoredMovie> picks = recommendations.recommend(userId, genre, count, parseIds(exclude));
        UserProfile user = users.findById(userId).orElse(null);

        return picks.stream()
                .map(scored -> {
                    String why = user == null ? null
                            : explanations.explain(user, scored.movie(), genre);
                    return RecommendationDto.from(scored, why);
                })
                .toList();
    }

    @PostMapping("/react")
    public Map<String, Object> react(@RequestBody ReactRequest request) {
        tasteProfile.react(request);
        return Map.of("ok", true);
    }

    /** Parses the comma-separated "already shown this session" ids used to page without repeats. */
    private Set<Long> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    ids.add(Long.parseLong(trimmed));
                } catch (NumberFormatException ignored) {
                    // Skip junk ids rather than failing the whole request.
                }
            }
        }
        return ids;
    }
}
