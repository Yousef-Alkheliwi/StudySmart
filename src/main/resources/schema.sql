-- StudySmart schema (SQLite).
-- Applied on every boot (idempotent via IF NOT EXISTS) - see spring.sql.init in application.yml.

CREATE TABLE IF NOT EXISTS projects (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS documents (
    id            TEXT PRIMARY KEY,
    project_id    TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    filename      TEXT NOT NULL,
    content_type  TEXT NOT NULL,
    size_bytes    INTEGER NOT NULL,
    page_count    INTEGER,
    stored_path   TEXT NOT NULL,
    status        TEXT NOT NULL,
    error_message TEXT,
    created_at    TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_documents_project ON documents(project_id);

CREATE TABLE IF NOT EXISTS chunks (
    id          TEXT PRIMARY KEY,
    document_id TEXT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    project_id  TEXT NOT NULL,
    ordinal     INTEGER NOT NULL,
    page        INTEGER,
    content     TEXT NOT NULL,
    char_start  INTEGER NOT NULL,
    char_end    INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chunks_document ON chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_chunks_project ON chunks(project_id);

-- One dense vector per chunk, produced by the on-device embedding model.
-- Stored as little-endian float32 bytes; searched in memory per project.
CREATE TABLE IF NOT EXISTS chunk_embeddings (
    chunk_id   TEXT PRIMARY KEY REFERENCES chunks(id) ON DELETE CASCADE,
    project_id TEXT NOT NULL,
    model      TEXT NOT NULL,
    dims       INTEGER NOT NULL,
    vector     BLOB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_chunk_embeddings_project ON chunk_embeddings(project_id);

CREATE TABLE IF NOT EXISTS chat_sessions (
    id         TEXT PRIMARY KEY,
    project_id TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title      TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_sessions_project ON chat_sessions(project_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id         TEXT PRIMARY KEY,
    session_id TEXT NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role       TEXT NOT NULL,
    content    TEXT NOT NULL,
    created_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_messages_session ON chat_messages(session_id);

CREATE TABLE IF NOT EXISTS message_citations (
    id             TEXT PRIMARY KEY,
    message_id     TEXT NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    citation_order INTEGER NOT NULL,
    document_id    TEXT NOT NULL,
    chunk_id       TEXT,
    page           INTEGER,
    quoted_text    TEXT
);
CREATE INDEX IF NOT EXISTS idx_citations_message ON message_citations(message_id);

CREATE TABLE IF NOT EXISTS quizzes (
    id           TEXT PRIMARY KEY,
    project_id   TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    document_ids TEXT NOT NULL,
    created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_quizzes_project ON quizzes(project_id);

CREATE TABLE IF NOT EXISTS quiz_questions (
    id                 TEXT PRIMARY KEY,
    quiz_id            TEXT NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    ordinal            INTEGER NOT NULL,
    type               TEXT NOT NULL,
    prompt             TEXT NOT NULL,
    choices            TEXT,
    answer             TEXT NOT NULL,
    explanation        TEXT,
    source_document_id TEXT,
    source_filename    TEXT,
    source_page        INTEGER
);
CREATE INDEX IF NOT EXISTS idx_questions_quiz ON quiz_questions(quiz_id);

CREATE TABLE IF NOT EXISTS summaries (
    id           TEXT PRIMARY KEY,
    project_id   TEXT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title        TEXT NOT NULL,
    document_ids TEXT NOT NULL,
    content      TEXT NOT NULL,
    created_at   TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_summaries_project ON summaries(project_id);

CREATE TABLE IF NOT EXISTS summary_citations (
    id           TEXT PRIMARY KEY,
    summary_id   TEXT NOT NULL REFERENCES summaries(id) ON DELETE CASCADE,
    document_id  TEXT NOT NULL,
    page         INTEGER,
    quoted_text  TEXT
);
CREATE INDEX IF NOT EXISTS idx_summary_citations_summary ON summary_citations(summary_id);
