package com.studysmart.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
@EnableConfigurationProperties(StudySmartProperties.class)
public class AppConfig implements WebMvcConfigurer {

    /**
     * Document ingestion (text extraction, chunking, indexing, embedding)
     * runs off the request thread so an upload of a large PDF returns
     * immediately with status PENDING while processing continues.
     */
    @Bean(name = "ingestionExecutor")
    public Executor ingestionExecutor() {
        return Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "studysmart-ingest");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
