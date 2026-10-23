package com.exam.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class MarkingDtos {

    private MarkingDtos() {
    }

    public record PendingItem(Long attemptId, Long planId, String planName, String studentName,
                              LocalDateTime submittedAt, BigDecimal score, BigDecimal totalScore,
                              boolean passed, int essayTotal, int essayMarked) {
    }

    public record ReviewItem(Long answerId, Long questionId, String type, String stem,
                             String myAnswer, String correctAnswer, String analysis,
                             BigDecimal fullScore, BigDecimal score, String comment, Boolean manual) {
    }

    public record AttemptReview(Long attemptId, String planName, String studentName,
                                BigDecimal score, BigDecimal totalScore, boolean passed,
                                LocalDateTime submittedAt, List<ReviewItem> items) {
    }

    public record SaveScoreReq(BigDecimal score, String comment) {
    }

    public record ProctorRow(Long attemptId, String studentName, String status,
                             LocalDateTime startedAt, long remainingSec, int answeredCount,
                             BigDecimal score, int switchCount, LocalDateTime lastSwitchAt,
                             boolean suspicious) {
    }
}
