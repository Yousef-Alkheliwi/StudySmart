package com.studysmart;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class StudySmartApplication {
    public static void main(String[] args) {
        // The SQLite datasource bean opens a connection during context
        // refresh, before any of our own beans run - so the data directory
        // has to exist before Spring even starts, not after.
        createDataDirectories();
        SpringApplication.run(StudySmartApplication.class, args);
    }

    private static void createDataDirectories() {
        String dataDir = System.getenv().getOrDefault("STUDYSMART_DATA_DIR", "./data");
        try {
            Files.createDirectories(Path.of(dataDir));
            Files.createDirectories(Path.of(dataDir, "uploads"));
            Files.createDirectories(Path.of(dataDir, "lucene"));
        } catch (IOException e) {
            throw new IllegalStateException("Could not create StudySmart data directories under " + dataDir, e);
        }
    }
}
