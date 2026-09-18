package com.studysmart.service;

import com.studysmart.domain.GradedAnswer;
import com.studysmart.local.GradeResult;
import com.studysmart.local.LexicalGrader;
import org.springframework.stereotype.Service;

/** Grades a free-text quiz answer with the on-device lexical grader. */
@Service
public class GradingService {

    public GradedAnswer grade(String referenceAnswer, String studentAnswer) {
        GradeResult result = LexicalGrader.grade(referenceAnswer, studentAnswer);
        return new GradedAnswer(result.correct(), result.feedback(), referenceAnswer);
    }
}
