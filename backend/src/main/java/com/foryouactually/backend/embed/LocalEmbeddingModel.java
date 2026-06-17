package com.foryouactually.backend.embed;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Local, quota-free embeddings powered by DJL + a sentence-transformers model
 * (default: all-MiniLM-L6-v2, 384-dim). Runs entirely on-box — no API key, no daily cap,
 * works offline once the model is cached. Active by default ({@code fya.embedding.provider=local}).
 *
 * <p>The model + PyTorch native library are downloaded once on first load and cached under
 * {@code $HOME/.djl.ai}.
 */
@Component
@ConditionalOnProperty(name = "fya.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalEmbeddingModel implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(LocalEmbeddingModel.class);

    private final String modelUrl;
    private final int maxBatch;
    private ZooModel<String, float[]> model;

    public LocalEmbeddingModel(
            @Value("${fya.embedding.local.model-url:djl://ai.djl.huggingface.pytorch/sentence-transformers/all-MiniLM-L6-v2}") String modelUrl,
            @Value("${fya.embedding.local.max-batch:64}") int maxBatch) {
        this.modelUrl = modelUrl;
        this.maxBatch = maxBatch;
    }

    @PostConstruct
    void load() throws Exception {
        log.info("Loading local embedding model: {}", modelUrl);
        long started = System.currentTimeMillis();
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(modelUrl)
                .optEngine("PyTorch")
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                // L2-normalise so vectors are unit length (clean cosine similarity).
                .optArgument("normalize", "true")
                .build();
        this.model = criteria.loadModel();
        log.info("Local embedding model ready in {} ms", System.currentTimeMillis() - started);
    }

    @PreDestroy
    void shutdown() {
        if (model != null) {
            model.close();
        }
    }

    @Override
    public float[] embed(String text) {
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return predictor.predict(text);
        } catch (Exception e) {
            throw new IllegalStateException("Local embedding failed", e);
        }
    }

    @Override
    public List<float[]> batchEmbed(List<String> texts) {
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return new ArrayList<>(predictor.batchPredict(texts));
        } catch (Exception e) {
            throw new IllegalStateException("Local batch embedding failed", e);
        }
    }

    @Override
    public int maxBatch() {
        return maxBatch;
    }

    @Override
    public String id() {
        return "local:" + modelUrl.substring(modelUrl.lastIndexOf('/') + 1);
    }
}
