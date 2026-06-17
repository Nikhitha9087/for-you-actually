package com.foryouactually.backend.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foryouactually.backend.model.WatchInfo;

/** Serialises {@link WatchInfo} to/from the single JSON column it lives in on a movie row. */
public final class WatchUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WatchUtil() {
    }

    public static String toJson(WatchInfo info) {
        try {
            return MAPPER.writeValueAsString(info);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize watch info", e);
        }
    }

    /** Returns null for missing/garbage data so callers can simply omit the section. */
    public static WatchInfo fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, WatchInfo.class);
        } catch (Exception e) {
            return null;
        }
    }
}
