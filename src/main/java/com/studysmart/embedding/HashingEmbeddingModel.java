package com.studysmart.embedding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A dependency-free, deterministic embedding built by feature-hashing word
 * unigrams and bigrams into a fixed-size vector (the "hashing trick").
 *
 * <p>It captures lexical overlap, not meaning - "car" and "automobile" are
 * unrelated to it - but it needs no model download, runs in microseconds,
 * and is fully reproducible, which makes it the right choice for unit tests
 * and the safety net when the learned model can't be loaded (first run with
 * no network, unsupported CPU, and so on). Search still works in that case,
 * it's just lexical rather than semantic.
 */
public final class HashingEmbeddingModel implements EmbeddingModel {

    public static final String NAME = "hashing-v1";
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private final int dimensions;

    public HashingEmbeddingModel() {
        this(1024);
    }

    public HashingEmbeddingModel(int dimensions) {
        if (dimensions < 16) {
            throw new IllegalArgumentException("dimensions must be at least 16");
        }
        this.dimensions = dimensions;
    }

    @Override
    public String name() {
        return NAME + "-" + dimensions;
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public float relevanceFloor() {
        return 0.08f;
    }

    @Override
    public float[] embed(String text) {
        float[] v = new float[dimensions];
        // Stopwords are dropped before hashing: otherwise "what is the ..."
        // shares features with every sentence and nothing looks unrelated.
        List<String> tokens = tokenize(text).stream().filter(t -> !Stopwords.isStopword(t)).toList();
        for (int i = 0; i < tokens.size(); i++) {
            add(v, tokens.get(i), 1.0f);
            if (i + 1 < tokens.size()) {
                add(v, tokens.get(i) + " " + tokens.get(i + 1), 0.5f);
            }
        }
        Vectors.normalizeInPlace(v);
        return v;
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> out = new ArrayList<>(texts.size());
        for (String t : texts) {
            out.add(embed(t));
        }
        return out;
    }

    private void add(float[] v, String feature, float weight) {
        int h = murmurMix(feature.hashCode());
        int index = Math.floorMod(h, dimensions);
        // A second hash decides the sign, which keeps the expected dot product
        // of unrelated texts near zero instead of always positive.
        float sign = (murmurMix(h) & 1) == 0 ? 1f : -1f;
        v[index] += sign * weight;
    }

    private static int murmurMix(int h) {
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }
}
