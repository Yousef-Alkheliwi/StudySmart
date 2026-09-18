package com.studysmart.web.dto;

import java.util.List;

public record CreateSummaryRequest(List<String> documentIds) {

    public List<String> documentIdsOrEmpty() {
        return documentIds == null ? List.of() : documentIds;
    }
}
