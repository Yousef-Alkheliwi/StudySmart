package com.studysmart.web;

import com.studysmart.domain.Summary;
import com.studysmart.exception.NotFoundException;
import com.studysmart.service.SummaryService;
import com.studysmart.repository.SummaryRepository;
import com.studysmart.service.ProjectService;
import com.studysmart.web.dto.CreateSummaryRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class SummaryController {

    private final ProjectService projectService;
    private final SummaryService summaryService;
    private final SummaryRepository summaryRepository;

    public SummaryController(ProjectService projectService, SummaryService summaryService, SummaryRepository summaryRepository) {
        this.projectService = projectService;
        this.summaryService = summaryService;
        this.summaryRepository = summaryRepository;
    }

    @PostMapping("/api/projects/{projectId}/summaries")
    public ResponseEntity<Summary> create(@PathVariable String projectId, @RequestBody CreateSummaryRequest request) {
        projectService.get(projectId);
        Summary summary = summaryService.summarize(projectId, request.documentIdsOrEmpty());
        return ResponseEntity.status(HttpStatus.CREATED).body(summary);
    }

    @GetMapping("/api/projects/{projectId}/summaries")
    public List<Summary> listByProject(@PathVariable String projectId) {
        projectService.get(projectId);
        return summaryRepository.findByProject(projectId);
    }

    @GetMapping("/api/summaries/{id}")
    public Summary get(@PathVariable String id) {
        return summaryRepository.findById(id).orElseThrow(() -> new NotFoundException("Summary not found: " + id));
    }
}
