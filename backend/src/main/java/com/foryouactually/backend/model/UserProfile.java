package com.foryouactually.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * One person's taste. The heart of it is {@code tasteVectors}: one taste dot per genre,
 * each a point on the map of feelings. Loving slow-burn thrillers and rom-coms can coexist
 * because they live in separate dots and are never averaged together.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    private String id;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "taste_vectors", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "genre")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "vector", length = 100000)
    private Map<Genre, String> tasteVectors = new HashMap<>();

    /**
     * The user's own words about what they love in each genre ("title. why" from onboarding).
     * The richest signal we have — used to ground the LLM's "why you'll like this" notes in
     * the viewer's own voice rather than generic praise.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seed_reasons", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "genre")
    @MapKeyEnumerated(EnumType.STRING)
    @Column(name = "reason", length = 2000)
    private Map<Genre, String> seedReasons = new HashMap<>();

    /** Movies the user has already reacted to — excluded from future recommendations. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "seen_movies", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "movie_id")
    private Set<Long> seenMovieIds = new HashSet<>();

    /** Movies the user explicitly pushed away ("not for me") — real signal for the "you avoid" mirror. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "disliked_movies", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "movie_id")
    private Set<Long> dislikedMovieIds = new HashSet<>();
}
