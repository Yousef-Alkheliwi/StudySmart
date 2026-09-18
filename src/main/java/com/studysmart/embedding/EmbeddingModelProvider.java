package com.studysmart.embedding;

import com.studysmart.config.StudySmartProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Owns the process-wide embedding model and decides which one runs.
 *
 * <p>The learned ONNX model takes a few seconds to load (longer on the very
 * first run, when it downloads), so it's loaded on a background thread at
 * startup rather than blocking boot or the first request. Until it's ready,
 * and if it can never be loaded, search degrades to the lexical hashing
 * model - the app stays usable, it's just less clever about synonyms.
 */
@Component
public class EmbeddingModelProvider {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelProvider.class);

    private final StudySmartProperties properties;
    private final HashingEmbeddingModel fallback = new HashingEmbeddingModel();
    private volatile EmbeddingModel active;
    private volatile String status = "starting";
    private CompletableFuture<Void> warmup = CompletableFuture.completedFuture(null);

    public EmbeddingModelProvider(StudySmartProperties properties) {
        this.properties = properties;
        String provider = properties.getEmbeddings().getProvider().trim().toLowerCase(Locale.ROOT);
        switch (provider) {
            case "off" -> {
                active = null;
                status = "off";
            }
            case "hashing" -> {
                active = fallback;
                status = "hashing";
            }
            case "local" -> {
                active = fallback;
                status = "loading";
                // Not the common ForkJoinPool: inside a Spring Boot fat jar its
                // threads carry the system classloader, which can't see the
                // ServiceLoader files DJL uses to find its model zoo.
                warmup = CompletableFuture.runAsync(this::loadLocalModel, runnable -> {
                    Thread t = new Thread(runnable, "studysmart-embedding-load");
                    t.setDaemon(true);
                    t.setContextClassLoader(EmbeddingModelProvider.class.getClassLoader());
                    t.start();
                });
            }
            default -> throw new IllegalArgumentException(
                    "studysmart.embeddings.provider must be local, hashing or off (got '" + provider + "')");
        }
    }

    private void loadLocalModel() {
        try {
            active = LocalOnnxEmbeddingModel.load(properties.modelsDir());
            status = "local";
        } catch (Throwable t) {
            status = "hashing (local model unavailable)";
            log.warn("Could not load the on-device embedding model; semantic search will use the lexical "
                    + "fallback until restart. Cause: {}", t.toString());
        }
    }

    /** Empty when embeddings are switched off entirely. */
    public Optional<EmbeddingModel> current() {
        return Optional.ofNullable(active);
    }

    /** The active model, or the hashing model when embeddings are off - the on-device engine always needs <em>some</em> vector space. */
    public EmbeddingModel currentOrHashing() {
        EmbeddingModel model = active;
        return model != null ? model : fallback;
    }

    /** Blocks until the startup load finishes - used by ingestion so chunks get the good model's vectors, not the stopgap's. */
    public Optional<EmbeddingModel> ready() {
        warmup.join();
        return current();
    }

    /**
     * Gives the real model a short chance to finish loading before serving a
     * request. Without this, a question asked in the seconds after startup is
     * answered by the lexical stopgap and quietly comes back worse. Once the
     * load has settled - either way - this returns immediately, so a model
     * that can never load costs one wait, not one per request.
     */
    public EmbeddingModel awaitReady(Duration timeout) {
        try {
            warmup.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.debug("Embedding model still loading after {}; using the lexical fallback for this request", timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // The load failed; loadLocalModel has already logged and set the status.
        }
        return currentOrHashing();
    }

    public String status() {
        return status;
    }

    @PreDestroy
    void shutdown() {
        if (active instanceof LocalOnnxEmbeddingModel local) {
            local.close();
        }
    }
}
