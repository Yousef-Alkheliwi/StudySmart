package com.studysmart.web;

import com.studysmart.domain.Project;
import com.studysmart.service.ProjectService;
import com.studysmart.web.dto.CreateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<Project> create(@Valid @RequestBody CreateProjectRequest request) {
        Project project = projectService.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(project);
    }

    @GetMapping
    public List<Project> list() {
        return projectService.list();
    }

    @GetMapping("/{id}")
    public Project get(@PathVariable String id) {
        return projectService.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        projectService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
