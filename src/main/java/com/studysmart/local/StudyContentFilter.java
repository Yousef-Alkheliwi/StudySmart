package com.studysmart.local;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Separates the subject matter in a document from the scaffolding around it.
 *
 * <p>Course material rarely arrives clean. A lecture PDF opens with the
 * instructor's name, office hours and an email address; a chapter opens with
 * "In this chapter we will examine..."; a slide deck ends with a reading
 * list. None of that is worth being quizzed on or summarized, even though it
 * sits in the same file as the material that is - so quiz and summary
 * generation ask here first.
 *
 * <p>Questions and answers deliberately do <em>not</em> use this filter: if
 * a student asks when office hours are, the honest thing is to find it.
 */
public final class StudyContentFilter {

    /** "Instructor: Dr. Smith", "Office hours - Tue 2pm", "Course code: BIO 101". */
    private static final Pattern LABELLED_FRONT_MATTER = Pattern.compile(
            "^\\s*(instructor|professor|lecturer|teacher|ta|teaching assistant|office hours?|office|email|e-mail"
                    + "|phone|tel|telephone|course|course code|course title|class|room|building|campus|semester|term"
                    + "|quarter|year|credits?|units?|prerequisites?|pre-requisites?|textbook|required text|readings?"
                    + "|grading|assessment|evaluation|attendance|syllabus|schedule|contact|website|url|department"
                    + "|faculty|university|college|school|section|lecture|week|unit|module|chapter|page|date|time"
                    + "|location|venue|due|deadline|objectives?|outcomes?|agenda|outline)\\s*[:\\-–—]",
            Pattern.CASE_INSENSITIVE);

    /** Contact details are never subject matter. */
    private static final Pattern CONTACT_DETAILS = Pattern.compile(
            "[\\w.+-]+@[\\w-]+\\.[\\w.]+"                       // email
                    + "|https?://\\S+|www\\.\\S+"                // url
                    + "|\\+?\\d[\\d\\s().-]{7,}\\d",             // phone
            Pattern.CASE_INSENSITIVE);

    /** Administrative business: how the course is run, not what it teaches. */
    private static final Pattern ADMINISTRATIVE = Pattern.compile(
            "\\b(office hours?|syllabus|prerequisites?|textbook|required reading|recommended reading"
                    + "|grading (policy|scheme|criteria)|will be graded|final grade|counts? towards? your grade"
                    + "|worth \\d+ ?%|\\d+ ?% of (the|your) (grade|mark)|attendance (policy|is)|academic integrity"
                    + "|plagiarism|late submissions?|submit your|hand in your|due (date|on|by)|is due"
                    + "|exam (will be|is) held|midterm (is|will)|enrolment|enrollment|drop deadline|course website"
                    + "|teaching assistants?|contact me|email me|reach me at|my office|all rights reserved|copyright)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * Talk about the material rather than the material itself: chapter
     * introductions, learning objectives, "welcome to the course". Anchored
     * on the noun so a real sentence like "This process releases energy"
     * is untouched.
     */
    private static final Pattern COURSE_META = Pattern.compile(
            "\\b((in |for |throughout )?(this|the following|the next|today's) (chapter|lecture|course|class|section"
                    + "|unit|module|slide|slides|presentation|deck|book|text|tutorial|lab|session|week|semester))\\b"
                    + "|\\b(we|you|students?) (will|shall|are going to) (learn|study|cover|discuss|examine|explore"
                    + "|look at|review|see|be able to|understand|investigate)\\b"
                    + "|\\bby the end of (this|the)\\b"
                    + "|\\b(learning (objectives?|outcomes?|goals?)|course (overview|description|outline|objectives?)"
                    + "|table of contents|further reading|references|bibliography|acknowledge?ments?)\\b"
                    + "|\\bwelcome to\\b",
            Pattern.CASE_INSENSITIVE);

    private StudyContentFilter() {
    }

    /** True when the sentence teaches something a student could sensibly be tested on. */
    public static boolean isTestableContent(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return false;
        }
        String s = sentence.trim();
        return !LABELLED_FRONT_MATTER.matcher(s).find()
                && !CONTACT_DETAILS.matcher(s).find()
                && !ADMINISTRATIVE.matcher(s).find()
                && !COURSE_META.matcher(s).find()
                && !isMostlyNumbersAndDates(s);
    }

    /** Timetable rows and page furniture: "Mon 09:00 - 11:00 Room 4.21". */
    private static boolean isMostlyNumbersAndDates(String s) {
        String lower = s.toLowerCase(Locale.ROOT);
        boolean hasClockTime = lower.matches(".*\\b\\d{1,2}[:.]\\d{2}\\s*(am|pm)?\\b.*")
                || lower.matches(".*\\b\\d{1,2}\\s*(am|pm)\\b.*");
        long digits = s.chars().filter(Character::isDigit).count();
        long letters = s.chars().filter(Character::isLetter).count();
        return hasClockTime || (letters > 0 && digits > letters / 2);
    }
}
