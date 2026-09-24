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
    // Anything on screen belongs to the project we are leaving; clear it before
    // the new one loads so no course ever shows another course's material.
    clearProjectViews();
    // Land on chat before awaiting anything: doing it afterwards yanked the
    // user back here if they picked a different tab while the project loaded.
    switchTab("chat");
    await Promise.all([loadSessions(), loadDocuments()]);
}

/** Empties every panel that renders one project's content. */
function clearProjectViews() {
    stopDocumentPoll();
    el("chat-messages").innerHTML = "";
    el("quiz-list").innerHTML = "";
    el("quiz-detail").innerHTML = "";
    el("summary-list").innerHTML = "";
    const summaryDetail = el("summary-detail");
    if (summaryDetail) summaryDetail.innerHTML = "";
    state.quizzes = [];
    state.summaries = [];
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
    clearProjectViews();
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

let documentPoll = null;

/**
 * Watches an upload until it finishes processing. It has to stop when the
 * project is closed or deleted, otherwise it keeps asking for a project that
 * is no longer there - once every few seconds, for two minutes.
 */
function pollDocumentsUntilSettled() {
    const projectId = state.currentProjectId;
    stopDocumentPoll();

    const stop = () => stopDocumentPoll();
    documentPoll = setInterval(async () => {
        if (state.currentProjectId !== projectId) {
            stop();
            return;
        }
        try {
            await loadDocuments();
        } catch (err) {
            stop();
            return;
        }
        const stillProcessing = state.documents.some((d) => d.status === "PENDING" || d.status === "PROCESSING");
        if (!stillProcessing) stop();
    }, 2500);
    setTimeout(stop, 120000);
}

function stopDocumentPoll() {
    if (documentPoll !== null) {
        clearInterval(documentPoll);
        documentPoll = null;
    }
}

// ---------- Quizzes ----------

async function loadQuizzes() {
    const projectId = state.currentProjectId;
    const quizzes = await api(`/projects/${projectId}/quizzes`);
    if (projectId !== state.currentProjectId) return;   // the user moved on while this was in flight
    state.quizzes = quizzes;
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
        container.appendChild(renderQuestionCard(quiz, question, index));
    });
}

function renderQuestionCard(quiz, question, index) {
    const card = document.createElement("div");
    card.className = "question-card";

    const title = document.createElement("h4");
    title.textContent = `${index + 1}. ${question.prompt}`;
    card.appendChild(title);

    // The answer and the explanation stay hidden until asked for: the
    // explanation quotes the source sentence, so showing it would hand over
    // the answer before the student has tried.
    const answerPanel = document.createElement("div");
    answerPanel.className = "answer-panel";
    answerPanel.hidden = true;

    const answerLine = document.createElement("div");
    answerLine.className = "answer-box";
    answerLine.innerHTML = `<span class="answer-label">answer</span>`;
    answerLine.append(document.createTextNode(question.answer));
    answerPanel.appendChild(answerLine);

    if (question.explanation) {
        const explanation = document.createElement("div");
        explanation.className = "answer-box explanation";
        explanation.textContent = question.explanation;
        answerPanel.appendChild(explanation);
    }

    const actions = document.createElement("div");
    actions.className = "answer-actions";

    let choiceList = null;
    if (question.type === "MULTIPLE_CHOICE" && question.choices && question.choices.length) {
        choiceList = document.createElement("ul");
        choiceList.className = "choice-list";
        for (const choice of question.choices) {
            const li = document.createElement("li");
            li.textContent = choice;
            li.addEventListener("click", () => {
                clearChoiceMarks(choiceList);
                li.classList.add(choice === question.answer ? "correct" : "incorrect");
                if (choice !== question.answer) {
                    markCorrectChoice(choiceList, question.answer);
                }
            });
            choiceList.appendChild(li);
        }
        card.appendChild(choiceList);
    } else if (question.type === "SHORT_ANSWER") {
        card.appendChild(shortAnswerRow(quiz, question, actions));
    }

    const revealBtn = document.createElement("button");
    revealBtn.className = "reveal-btn";
    revealBtn.textContent = "show answer";
    revealBtn.addEventListener("click", () => {
        const showing = answerPanel.hidden;
        answerPanel.hidden = !showing;
        revealBtn.textContent = showing ? "hide answer" : "show answer";
        revealBtn.classList.toggle("revealed", showing);
        if (choiceList) {
            clearChoiceMarks(choiceList);
            if (showing) {
                markCorrectChoice(choiceList, question.answer);
            }
        }
    });
    actions.prepend(revealBtn);
    card.append(actions, answerPanel);

    if (question.sourceFilename) {
        const tag = document.createElement("div");
        tag.className = "source-tag";
        tag.textContent = question.sourcePage
            ? `Source: ${question.sourceFilename} — p.${question.sourcePage}`
            : `Source: ${question.sourceFilename}`;
        card.appendChild(tag);
    }
    return card;
}

function clearChoiceMarks(choiceList) {
    choiceList.querySelectorAll("li").forEach((n) => n.classList.remove("correct", "incorrect"));
}

function markCorrectChoice(choiceList, answer) {
    const correct = Array.from(choiceList.children).find((n) => n.textContent === answer);
    if (correct) correct.classList.add("correct");
}

/** Type-your-answer row; its "Check" button grades against the reference answer. */
function shortAnswerRow(quiz, question, actions) {
    const row = document.createElement("div");
    row.className = "short-answer-row";

    const input = document.createElement("input");
    input.type = "text";
    input.placeholder = "Your answer";

    const gradeBtn = document.createElement("button");
    gradeBtn.className = "btn btn-primary";
    gradeBtn.textContent = "check";

    const resultBox = document.createElement("div");

    const check = async () => {
        if (!input.value.trim()) return;
        gradeBtn.disabled = true;
        gradeBtn.textContent = "checking...";
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
            gradeBtn.textContent = "check";
        }
    };

    gradeBtn.addEventListener("click", check);
    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") {
            e.preventDefault();
            check();
        }
    });

    row.append(input, gradeBtn);
    const wrapper = document.createElement("div");
    wrapper.append(row, resultBox);
    return wrapper;
}

// ---------- Summaries ----------

async function loadSummaries() {
    const projectId = state.currentProjectId;
    const summaries = await api(`/projects/${projectId}/summaries`);
    if (projectId !== state.currentProjectId) return;   // the user moved on while this was in flight
    state.summaries = summaries;
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
