package com.studysmart.local;

import com.studysmart.domain.Citation;

import java.util.List;

public record AnswerResult(String text, List<Citation> citations) {
}
