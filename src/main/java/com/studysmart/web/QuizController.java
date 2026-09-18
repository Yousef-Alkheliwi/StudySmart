package com.studysmart.web;

import com.studysmart.domain.GradedAnswer;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.Quiz;
import com.studysmart.domain.QuizQuestion;
import com.studysmart.exception.NotFoundException;
import com.studysmart.service.GradingService;
import com.studysmart.service.QuizGenerationService;
import com.studysmart.repository.QuizRepository;
import com.studysmart.service.ProjectService;
import com.studysmart.web.dto.CreateQuizRequest;
import com.studysmart.web.dto.GradeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class QuizController {

    private final ProjectService projectService;
    private final QuizGenerationService quizGenerationService;
    private final QuizRepository quizRepository;
    private final GradingService gradingService;

    public QuizController(
            ProjectService projectService,
            QuizGenerationService quizGenerationService,
            QuizRepository quizRepository,
            GradingService gradingService
    ) {
        this.projectService = projectService;
        this.quizGenerationService = quizGenerationService;
        this.quizRepository = quizRepository;
        this.gradingService = gradingService;
    }

    @PostMapping("/api/projects/{projectId}/quizzes")
    public ResponseEntity<Quiz> create(@PathVariable String projectId, @RequestBody CreateQuizRequest request) {
        projectService.get(projectId);
        List<QuestionType> types = request.typesOrEmpty().stream().map(QuestionType::fromModelString).toList();
        Quiz quiz = quizGenerationService.generate(projectId, request.documentIdsOrEmpty(), request.questionCountOrDefault(), types);
        return ResponseEntity.status(HttpStatus.CREATED).body(quiz);
    }

    @GetMapping("/api/projects/{projectId}/quizzes")
    public List<Quiz> listByProject(@PathVariable String projectId) {
        projectService.get(projectId);
        return quizRepository.findByProject(projectId);
    }

    @GetMapping("/api/quizzes/{id}")
    public Quiz get(@PathVariable String id) {
        return quizRepository.findById(id).orElseThrow(() -> new NotFoundException("Quiz not found: " + id));
    }

    @PostMapping("/api/quizzes/{quizId}/questions/{questionId}/grade")
    public GradedAnswer grade(@PathVariable String quizId, @PathVariable String questionId, @Valid @RequestBody GradeRequest request) {
        Quiz quiz = get(quizId);
        QuizQuestion question = quiz.questions().stream()
                .filter(q -> q.id().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Question not found: " + questionId));
        return gradingService.grade(question.answer(), request.studentAnswer());
    }
}
