package com.foryouactually.backend.repository;

import com.foryouactually.backend.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MovieRepository extends JpaRepository<Movie, Long> {

    /** All fingerprinted films — small enough to filter in memory for accent-insensitive search. */
    List<Movie> findByEmbeddingJsonIsNotNull();
}
