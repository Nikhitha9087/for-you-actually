package com.foryouactually.backend.model;

import java.util.List;

/**
 * Where-to-watch info for a film: the streaming services that carry it (subscription/flatrate),
 * the region those services apply to, and a deep link to the full provider list (TMDB/JustWatch).
 * Stored per-movie as JSON and surfaced on recommendation cards.
 */
public record WatchInfo(String region, String link, List<Provider> providers) {

    /** A single streaming service, e.g. Netflix, with a logo to render as a chip. */
    public record Provider(String name, String logoUrl) {
    }
}
