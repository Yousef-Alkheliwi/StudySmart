package com.studysmart.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deleting a project or a document has to take its chunks, vectors,
 * messages and citations with it. That relies entirely on SQLite enforcing
 * foreign keys, which is off by default and only enabled per connection -
 * if the datasource ever stops passing that flag, deleted material would
 * quietly stay in the database and keep turning up in semantic search.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"studysmart.embeddings.provider=hashing"})
class DatabaseIntegrityTest {

    static Path dataDir;

    static {
        try {
            dataDir = Files.createTempDirectory("studysmart-cascade-test");
            Files.createDirectories(dataDir.resolve("uploads"));
            Files.createDirectories(dataDir.resolve("lucene"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void dataDirectory(DynamicPropertyRegistry registry) {
        registry.add("studysmart.data-dir", () -> dataDir.toString());
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ChunkRepository chunkRepository;

    @Autowired
    private TimestampNormalizer normalizer;

    @Test
    void foreignKeyEnforcementIsOnForTheConnectionsTheAppUses() {
        assertThat(jdbc.queryForObject("PRAGMA foreign_keys", Integer.class))
                .as("SQLite foreign key enforcement")
                .isEqualTo(1);
    }

    @Test
    void deletingAProjectRemovesEverythingUnderIt() {
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO projects (id,name,description,created_at) VALUES ('p','P',null,?)", now);
        jdbc.update("INSERT INTO documents (id,project_id,filename,content_type,size_bytes,page_count,stored_path,status,error_message,created_at)"
                + " VALUES ('d','p','f.txt','text/plain',10,null,'/x','READY',null,?)", now);
        jdbc.update("INSERT INTO chunks (id,document_id,project_id,ordinal,page,content,char_start,char_end)"
                + " VALUES ('c','d','p',0,1,'text',0,4)");
        jdbc.update("INSERT INTO chunk_embeddings (chunk_id,project_id,model,dims,vector) VALUES ('c','p','m',2,?)",
                new Object[]{new byte[]{1, 2, 3, 4}});
        jdbc.update("INSERT INTO chat_sessions (id,project_id,title,created_at,updated_at) VALUES ('s','p','t',?,?)", now, now);
        jdbc.update("INSERT INTO chat_messages (id,session_id,role,content,created_at) VALUES ('m','s','USER','hi',?)", now);
        jdbc.update("INSERT INTO message_citations (id,message_id,citation_order,document_id,chunk_id,page,quoted_text)"
                + " VALUES ('mc','m',0,'d','c',1,'text')");
        jdbc.update("INSERT INTO quizzes (id,project_id,title,document_ids,created_at) VALUES ('q','p','T','[]',?)", now);
        jdbc.update("INSERT INTO quiz_questions (id,quiz_id,ordinal,type,prompt,choices,answer,explanation,source_document_id,source_filename,source_page)"
                + " VALUES ('qq','q',0,'SHORT_ANSWER','p?','[]','a',null,'d','f.txt',1)");
        jdbc.update("INSERT INTO summaries (id,project_id,title,document_ids,content,created_at) VALUES ('sm','p','T','[]','c',?)", now);
        jdbc.update("INSERT INTO summary_citations (id,summary_id,document_id,page,quoted_text) VALUES ('sc','sm','d',1,'t')");

        jdbc.update("DELETE FROM projects WHERE id = 'p'");

        for (String table : new String[]{"documents", "chunks", "chunk_embeddings", "chat_sessions", "chat_messages",
                "message_citations", "quizzes", "quiz_questions", "summaries", "summary_citations"}) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class))
                    .as("rows left in %s after deleting the project", table)
                    .isZero();
        }
    }

    @Test
    void wholeProjectMaterialSkipsDocumentsThatNeverFinishedIngesting() {
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO projects (id,name,description,created_at) VALUES ('p3','P',null,?)", now);
        for (String[] doc : new String[][]{{"ready", "READY"}, {"failed", "FAILED"}, {"busy", "PROCESSING"}}) {
            jdbc.update("INSERT INTO documents (id,project_id,filename,content_type,size_bytes,page_count,stored_path,status,error_message,created_at)"
                    + " VALUES (?,'p3',?,'text/plain',10,null,'/x',?,null,?)", doc[0], doc[0] + ".txt", doc[1], now);
            jdbc.update("INSERT INTO chunks (id,document_id,project_id,ordinal,page,content,char_start,char_end)"
                    + " VALUES (?,?,'p3',0,1,?,0,4)", doc[0] + "-c", doc[0], "text of " + doc[0]);
        }

        // A quiz over "the whole project" must not be built from a file that
        // failed halfway, or one still being processed.
        assertThat(chunkRepository.findByProject("p3"))
                .extracting(c -> c.documentId())
                .containsExactly("ready");

        jdbc.update("DELETE FROM projects WHERE id = 'p3'");
    }

    @Test
    void oldTimestampsAreNormalisedSoRowsComeBackInTheRightOrder() {
        // Three sessions written the way earlier versions wrote them: same
        // millisecond, different fraction widths.
        jdbc.update("INSERT INTO projects (id,name,description,created_at) VALUES ('p4','P',null,?)",
                "2026-01-01T00:00:00Z");
        String[][] sessions = {
                {"s-late", "2026-09-23T10:00:00.123456Z"},
                {"s-early", "2026-09-23T10:00:00.123Z"},
                {"s-latest", "2026-09-23T10:00:00.9Z"}};
        for (String[] session : sessions) {
            jdbc.update("INSERT INTO chat_sessions (id,project_id,title,created_at,updated_at) VALUES (?,'p4',?,?,?)",
                    session[0], session[0], session[1], session[1]);
        }

        normalizer.normalise();

        assertThat(jdbc.queryForList(
                "SELECT id FROM chat_sessions WHERE project_id='p4' ORDER BY created_at ASC", String.class))
                .containsExactly("s-early", "s-late", "s-latest");
        assertThat(jdbc.queryForList(
                "SELECT length(created_at) FROM chat_sessions WHERE project_id='p4'", Integer.class))
                .allMatch(length -> length == Timestamps.STORED_LENGTH);

        jdbc.update("DELETE FROM projects WHERE id = 'p4'");
    }

    @Test
    void deletingOneDocumentRemovesItsChunksAndVectors() {
        String now = Instant.now().toString();
        jdbc.update("INSERT INTO projects (id,name,description,created_at) VALUES ('p2','P',null,?)", now);
        for (String d : new String[]{"d1", "d2"}) {
            jdbc.update("INSERT INTO documents (id,project_id,filename,content_type,size_bytes,page_count,stored_path,status,error_message,created_at)"
                    + " VALUES (?,'p2',?,'text/plain',10,null,'/x','READY',null,?)", d, d + ".txt", now);
            jdbc.update("INSERT INTO chunks (id,document_id,project_id,ordinal,page,content,char_start,char_end)"
                    + " VALUES (?,?,'p2',0,1,'text',0,4)", d + "-c", d);
            jdbc.update("INSERT INTO chunk_embeddings (chunk_id,project_id,model,dims,vector) VALUES (?,'p2','m',2,?)",
                    d + "-c", new byte[]{1, 2, 3, 4});
        }

        jdbc.update("DELETE FROM documents WHERE id = 'd1'");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE document_id='d1'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings WHERE chunk_id='d1-c'", Integer.class)).isZero();
        // The other document is untouched.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE document_id='d2'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings WHERE chunk_id='d2-c'", Integer.class)).isEqualTo(1);

        jdbc.update("DELETE FROM projects WHERE id = 'p2'");
    }
}
