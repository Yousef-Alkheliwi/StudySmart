package com.studysmart.web.dto;

import jakarta.validation.constraints.NotBlank;

public record GradeRequest(@NotBlank String studentAnswer) {
}
