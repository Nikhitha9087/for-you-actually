package com.foryouactually.backend.match;

import com.foryouactually.backend.model.Movie;

/** A movie paired with how closely it matches a target taste (1.0 = identical feel). */
public record ScoredMovie(Movie movie, double score) {
}
