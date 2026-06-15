package com.foryouactually.backend.ingest;

import com.foryouactually.backend.client.TmdbClient;
import com.foryouactually.backend.client.dto.TmdbMovieDto;
import com.foryouactually.backend.client.dto.TmdbPageDto;
import com.foryouactually.backend.model.Movie;
import com.foryouactually.backend.repository.MovieRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final TmdbClient tmdb;
    private final MovieRepository movies;

    public IngestionService(TmdbClient tmdb, MovieRepository movies) {
        this.tmdb = tmdb;
        this.movies = movies;
    }

    /** Fetches the given number of TMDB pages (20 movies each) and stores them. Returns how many were saved. */
    public int ingest(int pages) {
        int saved = 0;
        for (int page = 1; page <= pages; page++) {
            TmdbPageDto result = tmdb.discoverPopular(page);
            if (result == null || result.results() == null || result.results().isEmpty()) {
                break;
            }
            saved += saveAll(result);
            log.info("Ingested page {}/{} (running total saved this run: {})", page, pages, saved);
        }
        return saved;
    }

    /**
     * Builds a balanced multi-language catalogue by pulling popular films in each given language.
     * Saving keyed on TMDB id means re-runs are idempotent (a film appears once even if pulled twice).
     */
    public Map<String, Integer> ingestLanguages(List<String> languages, int pagesPerLanguage, int minVotes) {
        Map<String, Integer> savedPerLanguage = new LinkedHashMap<>();
        for (String language : languages) {
            int saved = 0;
            for (int page = 1; page <= pagesPerLanguage; page++) {
                TmdbPageDto result = tmdb.discoverByLanguage(language, page, minVotes);
                if (result == null || result.results() == null || result.results().isEmpty()) {
                    break;
                }
                saved += saveAll(result);
                if (page >= result.totalPages()) {
                    break;
                }
            }
            savedPerLanguage.put(language, saved);
            log.info("Ingested {} movies for language '{}'", saved, language);
        }
        return savedPerLanguage;
    }

    private int saveAll(TmdbPageDto page) {
        int saved = 0;
        for (TmdbMovieDto dto : page.results()) {
            if (dto.overview() == null || dto.overview().isBlank()) {
                continue;
            }
            movies.save(toMovie(dto));
            saved++;
        }
        return saved;
    }

    private Movie toMovie(TmdbMovieDto dto) {
        Movie movie = new Movie();
        movie.setId(dto.id());
        movie.setTitle(dto.title());
        movie.setOriginalTitle(dto.originalTitle());
        movie.setOriginalLanguage(dto.originalLanguage());
        movie.setOverview(dto.overview());
        movie.setReleaseYear(parseYear(dto.releaseDate()));
        movie.setPosterUrl(tmdb.toPosterUrl(dto.posterPath()));
        movie.setVoteAverage(dto.voteAverage());
        movie.setPopularity(dto.popularity());
        movie.setGenres(GenreMapper.toBuckets(dto.genreIds()));
        return movie;
    }

    private Integer parseYear(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
