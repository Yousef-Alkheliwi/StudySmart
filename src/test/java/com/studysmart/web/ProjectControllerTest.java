package com.studysmart.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysmart.domain.Project;
import com.studysmart.exception.NotFoundException;
import com.studysmart.service.ProjectService;
import com.studysmart.web.dto.CreateProjectRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProjectService projectService;

    @Test
    void createsAProjectAndReturns201() throws Exception {
        Project created = new Project("p1", "Organic Chemistry", "Fall semester", Instant.now());
        when(projectService.create("Organic Chemistry", "Fall semester")).thenReturn(created);

        mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest("Organic Chemistry", "Fall semester"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("p1"))
                .andExpect(jsonPath("$.name").value("Organic Chemistry"));
    }

    @Test
    void rejectsABlankProjectName() throws Exception {
        mockMvc.perform(post("/api/projects")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new CreateProjectRequest("  ", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsProjects() throws Exception {
        when(projectService.list()).thenReturn(List.of(
                new Project("p1", "Biology 101", null, Instant.now()),
                new Project("p2", "Linear Algebra", null, Instant.now())
        ));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void returns404ForAMissingProject() throws Exception {
        when(projectService.get("missing")).thenThrow(new NotFoundException("Project not found: missing"));

        mockMvc.perform(get("/api/projects/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Project not found: missing"));
    }

    @Test
    void deletesAProject() throws Exception {
        mockMvc.perform(delete("/api/projects/p1"))
                .andExpect(status().isNoContent());
    }
}
