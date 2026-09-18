package com.studysmart.search;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.Project;
import com.studysmart.repository.ChunkRepository;
import com.studysmart.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Rebuilds any keyword index that an older text-analysis version wrote.
 *
 * <p>The chunks themselves live in SQLite, so the Lucene index is a derived
 * artifact and can always be regenerated. Doing it at startup means
 * upgrading StudySmart never leaves a library searchable only by the rules
 * it was indexed under - the alternative is silently degraded results that
 * no one would think to debug.
 */
@Component
public class SearchIndexUpgrade {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexUpgrade.class);

    private final ProjectRepository projectRepository;
    private final ChunkRepository chunkRepository;
    private final LuceneIndexManager indexManager;

    public SearchIndexUpgrade(ProjectRepository projectRepository, ChunkRepository chunkRepository,
                              LuceneIndexManager indexManager) {
        this.projectRepository = projectRepository;
        this.chunkRepository = chunkRepository;
        this.indexManager = indexManager;
    }

    @Async("ingestionExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        for (Project project : projectRepository.findAll()) {
            if (!indexManager.isStale(project.id())) {
                continue;
            }
            List<Chunk> chunks = chunkRepository.findByProject(project.id());
            try {
                indexManager.deleteProject(project.id());
                indexManager.indexChunks(project.id(), chunks);
                log.info("Rebuilt the keyword index for \"{}\" ({} chunks) with analyzer {}",
                        project.name(), chunks.size(), LuceneIndexManager.ANALYZER_VERSION);
            } catch (IOException e) {
                log.warn("Could not rebuild the keyword index for project {}", project.id(), e);
            }
        }
    }
}
