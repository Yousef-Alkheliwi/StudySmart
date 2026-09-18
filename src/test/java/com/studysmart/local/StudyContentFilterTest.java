package com.studysmart.local;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyContentFilterTest {

    @Test
    void rejectsSyllabusFrontMatter() {
        assertThat(StudyContentFilter.isTestableContent("Instructor: Dr. Amina Farouk")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Course code: BIO 101")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Office hours: Tuesdays 2:00 pm in Room 4.21")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Email: a.farouk@university.edu")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("The teaching assistant for this section is Mark Lee.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Your final grade is 40% coursework and 60% exam.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Assignment 2 is due on 14 March.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Copyright 2026 University of Somewhere.")).isFalse();
    }

    @Test
    void rejectsTalkAboutTheMaterialRatherThanTheMaterial() {
        assertThat(StudyContentFilter.isTestableContent("In this chapter we will examine cellular respiration.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Welcome to Introductory Cell Biology.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("By the end of this lecture you will be able to describe glycolysis.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Learning objectives for the week are listed below.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("This lecture covers enzymes and catalysis.")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("Further reading is given at the end of the text.")).isFalse();
    }

    @Test
    void keepsRealSubjectMatter() {
        assertThat(StudyContentFilter.isTestableContent(
                "Cellular respiration breaks down glucose to release energy as ATP.")).isTrue();
        assertThat(StudyContentFilter.isTestableContent(
                "Glycolysis takes place in the cytoplasm and does not require oxygen.")).isTrue();
        // "This process" must survive: the noun is not a course-structure word.
        assertThat(StudyContentFilter.isTestableContent(
                "This process releases energy that the cell can use.")).isTrue();
        // "course" and "section" in their ordinary senses are still content.
        assertThat(StudyContentFilter.isTestableContent(
                "The course of the illness changes after the first week.")).isTrue();
        assertThat(StudyContentFilter.isTestableContent(
                "A cross section of the stem reveals the vascular bundles.")).isTrue();
        // Dates inside a fact are fine; the sentence is still mostly words.
        assertThat(StudyContentFilter.isTestableContent(
                "The French Revolution began in 1789 with the storming of the Bastille.")).isTrue();
    }

    @Test
    void rejectsTimetableRowsAndNumberSoup() {
        assertThat(StudyContentFilter.isTestableContent("Mon 09:00 - 11:00 Room 4.21")).isFalse();
        assertThat(StudyContentFilter.isTestableContent("1.2.3 4.5.6 7.8.9 10.11")).isFalse();
    }

    @Test
    void handlesEmptyInput() {
        assertThat(StudyContentFilter.isTestableContent(null)).isFalse();
        assertThat(StudyContentFilter.isTestableContent("   ")).isFalse();
    }
}
