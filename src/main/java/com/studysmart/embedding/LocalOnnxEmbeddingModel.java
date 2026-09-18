package com.studysmart.embedding;

import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * A real sentence-transformer (MiniLM-L6-v2, 384 dimensions) run entirely
 * on this machine through ONNX Runtime. The model is fetched once (about
 * 90 MB) into the data directory and never phones home afterwards; from
 * then on embedding a sentence is a local matrix multiply, with no API
 * involved at any point.
 */
public final class LocalOnnxEmbeddingModel implements EmbeddingModel, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingModel.class);

    public static final String NAME = "minilm-l6-v2-onnx";
    private static final String MODEL_URL = "djl://ai.djl.huggingface.onnxruntime/sentence-transformers/all-MiniLM-L6-v2";
    private static final int DIMENSIONS = 384;
    private static final int BATCH_SIZE = 32;

    private final ZooModel<String, float[]> model;
    private final Predictor<String, float[]> predictor;

    /** Loads (downloading on first use) the model into {@code cacheDir}. Throws if it cannot be loaded. */
    public static LocalOnnxEmbeddingModel load(Path cacheDir) throws Exception {
        // DJL reads its cache location once, at class-initialization time, so
        // this has to be set before the first DJL class is touched.
        System.setProperty("DJL_CACHE_DIR", cacheDir.toAbsolutePath().toString());
        long start = System.currentTimeMillis();

        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls(MODEL_URL)
                .optEngine("OnnxRuntime")
                .optArgument("normalize", "true")
                .build();

        ZooModel<String, float[]> model = criteria.loadModel();
        log.info("Loaded on-device embedding model {} in {} ms", NAME, System.currentTimeMillis() - start);
        return new LocalOnnxEmbeddingModel(model);
    }

    private LocalOnnxEmbeddingModel(ZooModel<String, float[]> model) {
        this.model = model;
        this.predictor = model.newPredictor();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public float relevanceFloor() {
        return 0.25f;
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            List<String> batch = texts.subList(from, Math.min(texts.size(), from + BATCH_SIZE));
            // A DJL Predictor is not thread-safe; ingestion and requests can
            // both be embedding at once, so serialize access here.
            synchronized (predictor) {
                try {
                    out.addAll(predictor.batchPredict(batch));
                } catch (Exception e) {
                    throw new IllegalStateException("Embedding failed", e);
                }
            }
        }
        return out;
    }

    @Override
    public void close() {
        predictor.close();
        model.close();
    }
}
