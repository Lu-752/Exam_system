package com.exam.content.grading;

/**
 * 六种题型。COMPOSITE 为「题冒题」（材料题），分值由子题累加，本身不直接判分。
 */
public enum QuestionType {
    SC, MC, TF, BLANK, ESSAY, COMPOSITE
}
