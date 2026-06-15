package com.foryouactually.backend.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "movies")
@Getter
@Setter
@NoArgsConstructor
public class Movie {

    /** TMDB's own movie id — reused as our primary key so we never store a title twice. */
    @Id
    private Long id;

    private String title;

    private String originalTitle;

    /** ISO code of the original language, e.g. "ko", "es", "ja". Drives the cross-language angle. */
    private String originalLanguage;

    /** The plot synopsis — the raw material the feeling fingerprint is built from. */
    @Column(length = 4000)
    private String overview;

    private Integer releaseYear;

    private String posterUrl;

    /** TMDB community rating (0–10), handy for breaking ties between similar-feeling picks. */
    private Double voteAverage;

    private Double popularity;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "genre")
    private Set<Genre> genres = new HashSet<>();

    /** The feeling fingerprint (embedding). Null until generated in a later stage. */
    @Column(length = 100000)
    private String embeddingJson;
}
