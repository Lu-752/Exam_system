package com.exam.exam.dto;

import com.exam.content.dto.Option;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class ExamDtos {

    private ExamDtos() {
    }

    public record PaperItemReq(Long questionId, BigDecimal score) {
    }

    public record PaperUpsert(String name, Long bankId, BigDecimal passScore, List<PaperItemReq> items) {
    }

    public record AutoRule(String type, int count, BigDecimal score) {
    }

    public record AutoPaperReq(String name, Long bankId, BigDecimal passScore, List<AutoRule> rules) {
    }

    public record PaperView(Long id, String name, BigDecimal totalScore, BigDecimal passScore,
                            int questionCount, LocalDateTime createdAt) {
    }

    public record PlanUpsert(String name, Long paperId, LocalDateTime startAt, LocalDateTime endAt,
                             Integer durationSec, BigDecimal passScore, Integer maxAttempts) {
    }

    public record PlanView(Long id, String name, Long paperId, String paperName,
                           LocalDateTime startAt, LocalDateTime endAt, Integer durationSec,
                           BigDecimal passScore, BigDecimal totalScore, int questionCount,
                           String status, Long attemptId, String attemptStatus,
                           BigDecimal attemptScore, Boolean passed, Boolean scorePublished) {
    }

    /** 考试中下发的题目：不含正确答案与解析；题冒题通过 children 下发子题 */
    public record ExamQuestion(Long id, String type, String stem, List<Option> options,
                               BigDecimal score, int sortNo, List<ExamQuestion> children) {
    }

    public record AttemptStart(Long attemptId, Long planId, String planName, BigDecimal passScore,
                               BigDecimal totalScore, long remainingSec, List<ExamQuestion> questions) {
    }

    public record AnswerItem(Long questionId, String answer) {
    }

    public record SubmitReq(List<AnswerItem> answers) {
    }

    public record ResultDetail(Long questionId, String type, String stem, String myAnswer,
                               String correct, boolean ok, BigDecimal score, BigDecimal fullScore,
                               String analysis, List<ResultDetail> children) {
    }

    public record ResultView(Long attemptId, String planName, BigDecimal score, BigDecimal totalScore,
                             boolean passed, int correctCount, int questionCount,
                             List<ResultDetail> details, Boolean published) {
    }

    /** 考试过程的异常事件上报（切屏 / 失焦 / 隐藏） */
    public record EventReq(String type, Integer count) {
    }

    /** 上报回执：当前累计切屏次数 + 是否达到预警阈值 */
    public record EventAck(int switchCount, boolean warned, LocalDateTime lastSwitchAt) {
    }
}
