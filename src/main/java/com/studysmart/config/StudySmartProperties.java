package com.studysmart.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "studysmart")
public class StudySmartProperties {

    private String dataDir = "./data";
    private Embeddings embeddings = new Embeddings();
    private Retrieval retrieval = new Retrieval();
    private Chunking chunking = new Chunking();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Absolute, normalized form of the configured data directory. A relative
     * path here (the default, "./data") must not leak into
     * {@code MultipartFile.transferTo(File)} - that method resolves a
     * relative destination against Tomcat's internal multipart temp
     * directory, not the JVM's working directory, so every derived path is
     * forced absolute at the source.
     */
    public Path dataDirPath() {
        return Path.of(dataDir).toAbsolutePath().normalize();
    }

    public Path uploadsDir() {
        return dataDirPath().resolve("uploads");
    }

    public Path luceneDir() {
        return dataDirPath().resolve("lucene");
    }

    public Path modelsDir() {
        return dataDirPath().resolve("models");
    }

    public Embeddings getEmbeddings() {
        return embeddings;
    }

    public void setEmbeddings(Embeddings embeddings) {
        this.embeddings = embeddings;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public void setRetrieval(Retrieval retrieval) {
        this.retrieval = retrieval;
    }

    public Chunking getChunking() {
        return chunking;
    }

    public void setChunking(Chunking chunking) {
        this.chunking = chunking;
    }

    /** Which sentence-embedding model powers semantic search and the on-device engine. */
    public static class Embeddings {
        /** {@code local} (ONNX MiniLM, downloaded once), {@code hashing} (no download, lexical only), or {@code off}. */
        private String provider = "local";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    public static class Retrieval {
        private int topK = 8;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }
    }

    public static class Chunking {
        private int targetWords = 180;
        private int overlapWords = 30;

        public int getTargetWords() {
            return targetWords;
        }

        public void setTargetWords(int targetWords) {
            this.targetWords = targetWords;
        }

        public int getOverlapWords() {
            return overlapWords;
        }

        public void setOverlapWords(int overlapWords) {
            this.overlapWords = overlapWords;
        }
    }
}
