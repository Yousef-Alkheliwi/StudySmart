package com.studysmart.local;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("A grading verdict on a student's short-answer response")
public record GradeResult(
        @JsonPropertyDescription("true if the student's answer is substantively correct, allowing for different phrasing")
        boolean correct,

        @JsonPropertyDescription("One or two sentences of feedback explaining the verdict, addressed to the student")
        String feedback
) {
}
