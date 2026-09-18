package com.studysmart.web.dto;

import java.util.List;

public record CreateQuizRequest(List<String> documentIds, Integer questionCount, List<String> types) {

    public List<String> documentIdsOrEmpty() {
        return documentIds == null ? List.of() : documentIds;
    }

    public int questionCountOrDefault() {
        if (questionCount == null || questionCount < 1) {
            return 5;
        }
        return Math.min(questionCount, 20);
    }

    public List<String> typesOrEmpty() {
        return types == null ? List.of() : types;
    }
}
