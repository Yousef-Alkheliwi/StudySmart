// StudySmart frontend - a small dependency-free SPA. Talks to the Java
// backend exclusively through the /api/* REST endpoints; no build step.

const state = {
    projects: [],
    currentProjectId: null,
    sessions: [],
    currentSessionId: null,
    documents: [],
    quizzes: [],
    summaries: [],
};

const el = (id) => document.getElementById(id);

async function api(path, options = {}) {
    const res = await fetch(`/api${path}`, {
        headers: options.body instanceof FormData ? {} : { "Content-Type": "application/json" },
        ...options,
    });
    if (!res.ok) {
        let message = `Request failed (${res.status})`;
        try {
            const body = await res.json();
            if (body.message) message = body.message;
        } catch (_) {
            /* ignore parse failure */
        }
        throw new Error(message);
    }
    if (res.status === 204) return null;
    return res.json();
}

// ---------- Projects ----------

async function loadProjects() {
    state.projects = await api("/projects");
    renderProjectList();
}

function renderProjectList() {
    const list = el("project-list");
    list.innerHTML = "";
    for (const project of state.projects) {
        const li = document.createElement("li");
        li.className = project.id === state.currentProjectId ? "active" : "";
        const name = document.createElement("span");
        name.className = "list-item-name";
        name.textContent = project.name;
        li.appendChild(name);
        li.addEventListener("click", () => selectProject(project.id));
        list.appendChild(li);
    }
}

async function selectProject(projectId) {
    state.currentProjectId = projectId;
    const project = state.projects.find((p) => p.id === projectId);
    if (!project) return;

    el("empty-state").hidden = true;
    el("main").hidden = false;
    el("session-section").hidden = false;
    el("project-name").textContent = project.name;
    el("project-description").textContent = project.description || "";

    renderProjectList();
    await Promise.all([loadSessions(), loadDocuments()]);
    switchTab("chat");
}

async function createProject() {
    const name = prompt("Project name (e.g. \"Organic Chemistry\"):");
    if (!name || !name.trim()) return;
    const description = prompt("Optional description:") || "";
    const project = await api("/projects", {
        method: "POST",
        body: JSON.stringify({ name: name.trim(), description }),
    });
    await loadProjects();
    await selectProject(project.id);
}

async function deleteCurrentProject() {
    if (!state.currentProjectId) return;
    if (!confirm("Delete this project and everything in it? This cannot be undone.")) return;
    await api(`/projects/${state.currentProjectId}`, { method: "DELETE" });
    state.currentProjectId = null;
    el("main").hidden = true;
    el("session-section").hidden = true;
    el("empty-state").hidden = false;
    await loadProjects();
}

// ---------- Chat sessions ----------

async function loadSessions() {
    state.sessions = await api(`/projects/${state.currentProjectId}/sessions`);
    if (!state.sessions.length) {
        const session = await api(`/projects/${state.currentProjectId}/sessions`, {
            method: "POST",
            body: JSON.stringify({ title: "New chat" }),
        });
        state.sessions = [session];
    }
    state.currentSessionId = state.sessions[0].id;
    renderSessionList();
    await loadMessages();
}

function renderSessionList() {
    const list = el("session-list");
    list.innerHTML = "";
    for (const session of state.sessions) {
        const li = document.createElement("li");
        li.className = session.id === state.currentSessionId ? "active" : "";
        const name = document.createElement("span");
        name.className = "list-item-name";
        name.textContent = session.title;
        li.appendChild(name);
        li.addEventListener("click", async () => {
            state.currentSessionId = session.id;
            renderSessionList();
            await loadMessages();
        });
        list.appendChild(li);
    }
}

async function createSession() {
    const session = await api(`/projects/${state.currentProjectId}/sessions`, {
        method: "POST",
        body: JSON.stringify({ title: "New chat" }),
    });
    state.sessions.unshift(session);
    state.currentSessionId = session.id;
    renderSessionList();
    await loadMessages();
}

async function loadMessages() {
    const messages = await api(`/sessions/${state.currentSessionId}/messages`);
    const container = el("chat-messages");
    container.innerHTML = "";
    if (!messages.length) {
        container.innerHTML = '<p class="empty-hint">Ask a question about this project\'s documents. Answers cite the exact source.</p>';
        return;
    }
    for (const message of messages) {
        container.appendChild(renderMessage(message));
    }
    container.scrollTop = container.scrollHeight;
}

async function loadStatus() {
    try {
        const status = await api("/status");
        const el_ = el("ai-status");
        el_.textContent = `ai: on-device engine\nsearch: ${status.embeddings}`;
        el_.title = "Everything runs on this machine - no external AI service is used";
        el_.style.whiteSpace = "pre-line";
    } catch (_) {
        el("ai-status").textContent = "ai status unavailable";
    }
}

function renderMessage(message) {
    const div = document.createElement("div");
    div.className = `message ${message.role.toLowerCase()}`;
    div.textContent = message.content;

    if (message.citations && message.citations.length) {
        const chipRow = document.createElement("div");
        chipRow.className = "citations";
        const seen = new Set();
        for (const citation of message.citations) {
            const name = citation.documentFilename || "source";
            const label = citation.page ? `${name} — p.${citation.page}` : name;
            if (seen.has(label)) continue;
            seen.add(label);
            const chip = document.createElement("span");
            chip.className = "citation-chip";
            chip.textContent = label;
            chip.title = citation.quotedText || "";
            chipRow.appendChild(chip);
        }
        div.appendChild(chipRow);
    }
    return div;
}

async function askQuestion(event) {
    event.preventDefault();
    const input = el("ask-input");
    const question = input.value.trim();
    if (!question || !state.currentSessionId) return;
    input.value = "";
    input.disabled = true;

    const container = el("chat-messages");
    if (container.querySelector(".empty-hint")) container.innerHTML = "";
    const userDiv = document.createElement("div");
    userDiv.className = "message user";
    userDiv.textContent = question;
    container.appendChild(userDiv);

    const pending = document.createElement("div");
    pending.className = "message assistant";
    pending.textContent = "Thinking...";
    container.appendChild(pending);
    container.scrollTop = container.scrollHeight;

    try {
        const message = await api(`/sessions/${state.currentSessionId}/ask`, {
            method: "POST",
            body: JSON.stringify({ question }),
        });
        pending.replaceWith(renderMessage(message));
    } catch (err) {
        pending.textContent = `Error: ${err.message}`;
        pending.classList.add("error");
    } finally {
        input.disabled = false;
        input.focus();
        container.scrollTop = container.scrollHeight;
    }
}

// ---------- Documents ----------

async function loadDocuments() {
    state.documents = await api(`/projects/${state.currentProjectId}/documents`);
    renderDocumentList();
    populateDocumentSelects();
}

function renderDocumentList() {
    const list = el("document-list");
    list.innerHTML = "";
    for (const doc of state.documents) {
        const li = document.createElement("li");
        li.className = "document-row";

        const name = document.createElement("span");
        name.className = "list-item-name";
        name.textContent = doc.filename + (doc.pageCount ? ` (${doc.pageCount}p)` : "");

        const badge = document.createElement("span");
        badge.className = `status-badge status-${doc.status}`;
        badge.textContent = doc.status;
        if (doc.status === "FAILED" && doc.errorMessage) badge.title = doc.errorMessage;

        const delBtn = document.createElement("button");
        delBtn.className = "icon-btn";
        delBtn.textContent = "×";
        delBtn.title = "Delete";
        delBtn.addEventListener("click", async () => {
            await api(`/documents/${doc.id}`, { method: "DELETE" });
            await loadDocuments();
        });

        li.append(name, badge, delBtn);
        list.appendChild(li);
    }
}

function populateDocumentSelects() {
    for (const selectId of ["quiz-documents", "summary-documents"]) {
        const select = el(selectId);
        select.innerHTML = "";
        for (const doc of state.documents) {
            if (doc.status !== "READY") continue;
            const option = document.createElement("option");
            option.value = doc.id;
            option.textContent = doc.filename;
            select.appendChild(option);
        }
    }
}

async function uploadDocument(event) {
    event.preventDefault();
    const input = el("upload-input");
    if (!input.files.length) return;
    const formData = new FormData();
    formData.append("file", input.files[0]);
    input.value = "";
    el("upload-name").textContent = "choose a PDF, .txt or .md file";

    await api(`/projects/${state.currentProjectId}/documents`, {
        method: "POST",
        body: formData,
    });
    await loadDocuments();
    pollDocumentsUntilSettled();
}

function pollDocumentsUntilSettled() {
    const interval = setInterval(async () => {
        await loadDocuments();
        const stillProcessing = state.documents.some((d) => d.status === "PENDING" || d.status === "PROCESSING");
        if (!stillProcessing) clearInterval(interval);
    }, 2500);
    setTimeout(() => clearInterval(interval), 120000);
}

// ---------- Quizzes ----------

async function loadQuizzes() {
    state.quizzes = await api(`/projects/${state.currentProjectId}/quizzes`);
    const list = el("quiz-list");
    list.innerHTML = "";
    for (const quiz of state.quizzes) {
        const row = document.createElement("div");
        row.className = "quiz-row";
        const name = document.createElement("span");
        name.textContent = `${quiz.title} (${quiz.questions.length} questions)`;
        const openBtn = document.createElement("button");
        openBtn.className = "btn btn-primary";
        openBtn.textContent = "Open";
        openBtn.addEventListener("click", () => renderQuizDetail(quiz));
        row.append(name, openBtn);
        list.appendChild(row);
    }
}

async function generateQuiz(event) {
    event.preventDefault();
    const count = parseInt(el("quiz-count").value, 10) || 5;
    const documentIds = Array.from(el("quiz-documents").selectedOptions).map((o) => o.value);
    const button = event.target.querySelector("button[type=submit]");
    button.disabled = true;
    button.textContent = "Generating...";
    try {
        const quiz = await api(`/projects/${state.currentProjectId}/quizzes`, {
            method: "POST",
            body: JSON.stringify({ documentIds, questionCount: count }),
        });
        await loadQuizzes();
        renderQuizDetail(quiz);
    } catch (err) {
        alert(`Could not generate quiz: ${err.message}`);
    } finally {
        button.disabled = false;
        button.textContent = "Generate quiz";
    }
}

function renderQuizDetail(quiz) {
    const container = el("quiz-detail");
    container.innerHTML = "";
    const heading = document.createElement("h3");
    heading.textContent = quiz.title;
    container.appendChild(heading);

    quiz.questions.forEach((question, index) => {
        const card = document.createElement("div");
        card.className = "question-card";

        const title = document.createElement("h4");
        title.textContent = `${index + 1}. ${question.prompt}`;
        card.appendChild(title);

        if (question.type === "MULTIPLE_CHOICE" && question.choices && question.choices.length) {
            const ul = document.createElement("ul");
            ul.className = "choice-list";
            for (const choice of question.choices) {
                const li = document.createElement("li");
                li.textContent = choice;
                li.addEventListener("click", () => {
                    ul.querySelectorAll("li").forEach((n) => n.classList.remove("correct", "incorrect"));
                    li.classList.add(choice === question.answer ? "correct" : "incorrect");
                    if (choice !== question.answer) {
                        const correctLi = Array.from(ul.children).find((n) => n.textContent === question.answer);
                        if (correctLi) correctLi.classList.add("correct");
                    }
                });
                ul.appendChild(li);
            }
            card.appendChild(ul);
        } else if (question.type === "FLASHCARD") {
            const revealBtn = document.createElement("button");
            revealBtn.className = "reveal-btn";
            revealBtn.textContent = "Show answer";
            const answerBox = document.createElement("div");
            answerBox.className = "answer-box";
            answerBox.hidden = true;
            answerBox.textContent = question.answer;
            revealBtn.addEventListener("click", () => {
                answerBox.hidden = !answerBox.hidden;
            });
            card.append(revealBtn, answerBox);
        } else {
            const row = document.createElement("div");
            row.className = "short-answer-row";
            const input = document.createElement("input");
            input.type = "text";
            input.placeholder = "Your answer";
            const gradeBtn = document.createElement("button");
            gradeBtn.className = "btn btn-primary";
            gradeBtn.textContent = "Check";
            const resultBox = document.createElement("div");
            gradeBtn.addEventListener("click", async () => {
                if (!input.value.trim()) return;
                gradeBtn.disabled = true;
                gradeBtn.textContent = "Checking...";
                try {
                    const result = await api(`/quizzes/${quiz.id}/questions/${question.id}/grade`, {
                        method: "POST",
                        body: JSON.stringify({ studentAnswer: input.value.trim() }),
                    });
                    resultBox.className = `grade-result ${result.correct ? "correct" : "incorrect"}`;
                    resultBox.textContent = result.feedback;
                } catch (err) {
                    resultBox.className = "grade-result incorrect";
                    resultBox.textContent = `Could not grade: ${err.message}`;
                } finally {
                    gradeBtn.disabled = false;
                    gradeBtn.textContent = "Check";
                }
            });
            row.append(input, gradeBtn);
            card.append(row, resultBox);
        }

        if (question.explanation) {
            const exp = document.createElement("div");
            exp.className = "answer-box";
            exp.textContent = question.explanation;
            card.appendChild(exp);
        }

        if (question.sourceFilename) {
            const tag = document.createElement("div");
            tag.className = "source-tag";
            tag.textContent = question.sourcePage
                ? `Source: ${question.sourceFilename} — p.${question.sourcePage}`
                : `Source: ${question.sourceFilename}`;
            card.appendChild(tag);
        }

        container.appendChild(card);
    });
}

// ---------- Summaries ----------

async function loadSummaries() {
    state.summaries = await api(`/projects/${state.currentProjectId}/summaries`);
    const list = el("summary-list");
    list.innerHTML = "";
    for (const summary of state.summaries) {
        const row = document.createElement("div");
        row.className = "summary-row";
        const name = document.createElement("span");
        name.textContent = summary.title;
        const openBtn = document.createElement("button");
        openBtn.className = "btn btn-primary";
        openBtn.textContent = "View";
        openBtn.addEventListener("click", () => renderSummaryDetail(summary));
        row.append(name, openBtn);
        list.appendChild(row);
    }
}

async function generateSummary(event) {
    event.preventDefault();
    const documentIds = Array.from(el("summary-documents").selectedOptions).map((o) => o.value);
    if (!documentIds.length) {
        alert("Select at least one document to summarize.");
        return;
    }
    const button = event.target.querySelector("button[type=submit]");
    button.disabled = true;
    button.textContent = "Summarizing...";
    try {
        const summary = await api(`/projects/${state.currentProjectId}/summaries`, {
            method: "POST",
            body: JSON.stringify({ documentIds }),
        });
        await loadSummaries();
        renderSummaryDetail(summary);
    } catch (err) {
        alert(`Could not summarize: ${err.message}`);
    } finally {
        button.disabled = false;
        button.textContent = "Summarize";
    }
}

function renderSummaryDetail(summary) {
    let box = el("summary-detail");
    if (!box) {
        box = document.createElement("div");
        box.id = "summary-detail";
        el("tab-summaries").appendChild(box);
    }
    box.innerHTML = "";
    const heading = document.createElement("h3");
    heading.textContent = summary.title;
    const content = document.createElement("div");
    content.className = "summary-content";
    content.textContent = summary.content;
    box.append(heading, content);
}

// ---------- Tabs ----------

function switchTab(tab) {
    document.querySelectorAll(".tab-btn").forEach((btn) => btn.classList.toggle("active", btn.dataset.tab === tab));
    document.querySelectorAll(".tab-panel").forEach((panel) => {
        panel.hidden = panel.id !== `tab-${tab}`;
    });
    if (tab === "quizzes") loadQuizzes();
    if (tab === "summaries") loadSummaries();
}

// ---------- Wiring ----------

function init() {
    el("new-project-btn").addEventListener("click", createProject);
    el("hero-new-project").addEventListener("click", createProject);
    el("upload-input").addEventListener("change", () => {
        const file = el("upload-input").files[0];
        el("upload-name").textContent = file ? file.name : "choose a PDF, .txt or .md file";
    });
    el("new-session-btn").addEventListener("click", createSession);
    el("delete-project-btn").addEventListener("click", deleteCurrentProject);
    el("ask-form").addEventListener("submit", askQuestion);
    el("upload-form").addEventListener("submit", uploadDocument);
    el("quiz-form").addEventListener("submit", generateQuiz);
    el("summary-form").addEventListener("submit", generateSummary);

    document.querySelectorAll(".tab-btn").forEach((btn) => {
        btn.addEventListener("click", () => switchTab(btn.dataset.tab));
    });

    loadProjects();
    loadStatus();
}

init();
