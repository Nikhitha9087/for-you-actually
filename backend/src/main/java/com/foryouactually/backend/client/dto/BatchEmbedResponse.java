package com.foryouactually.backend.client.dto;

import java.util.List;

/**
 * Gemini's response to a {@code batchEmbedContents} call: one vector per input text,
 * in the same order as the request. Batching up to 100 texts per call counts as a
 * single request against the rate limit, so it slashes how fast we burn the daily quota.
 */
public record BatchEmbedResponse(List<EmbedResponse.Embedding> embeddings) {
}
