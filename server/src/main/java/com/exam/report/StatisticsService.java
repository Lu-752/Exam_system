package com.exam.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.exam.entity.ExamAttempt;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.Paper;
import com.exam.exam.mapper.ExamAttemptMapper;
import com.exam.exam.mapper.ExamPlanMapper;
import com.exam.exam.mapper.PaperMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 成绩统计与分析：按考试计划聚合 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ExamPlanMapper planMapper;
    private final PaperMapper paperMapper;
    private final ExamAttemptMapper attemptMapper;

    public record PlanStat(Long planId, String planName, String paperName, LocalDateTime startAt,
                           LocalDateTime endAt, BigDecimal totalScore, BigDecimal passScore,
                           String status, int attemptCount, int submittedCount, int passCount,
                           BigDecimal passRate, BigDecimal avgScore, BigDecimal maxScore,
                           BigDecimal minScore) {
    }

    public record Bucket(String label, int count) {
    }

    public record StudentRow(Long attemptId, String studentName, BigDecimal score, String status,
                             boolean passed, LocalDateTime startedAt, LocalDateTime submittedAt) {
    }

    public record PlanDetail(PlanStat summary, List<Bucket> distribution, List<StudentRow> rows) {
    }

    /** 所有计划的成绩概览 */
    public List<PlanStat> planStats() {
        List<ExamPlan> plans = planMapper.selectList(Wrappers.<ExamPlan>lambdaQuery()
                .orderByDesc(ExamPlan::getStartAt));
        List<PlanStat> out = new ArrayList<>();
        for (ExamPlan p : plans) {
            out.add(stat(p, attemptsOf(p.getId())));
        }
        return out;
    }

    /** 单个计划的分布与明细 */
    public PlanDetail detail(Long planId) {
        ExamPlan p = planMapper.selectById(planId);
        if (p == null) {
            throw new BizException(404, "考试计划不存在");
        }
        List<ExamAttempt> attempts = attemptsOf(planId);
        PlanStat summary = stat(p, attempts);

        // 按得分率分 5 档：[0,20) [20,40) [40,60) [60,80) [80,100]
        int[] counts = new int[5];
        BigDecimal total = p == null ? BigDecimal.ZERO : totalOf(p);
        for (ExamAttempt a : attempts) {
            if (!"SUBMITTED".equals(a.getStatus()) || a.getScore() == null) continue;
            double ratio = total.compareTo(BigDecimal.ZERO) == 0
                    ? 0
                    : a.getScore().doubleValue() / total.doubleValue();
            int idx = (int) Math.floor(ratio * 5);
            if (idx > 4) idx = 4;
            if (idx < 0) idx = 0;
            counts[idx]++;
        }
        String[] labels = {"0-20%", "20-40%", "40-60%", "60-80%", "80-100%"};
        List<Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new Bucket(labels[i], counts[i]));
        }

        List<StudentRow> rows = attempts.stream()
                .sorted(Comparator.comparing((ExamAttempt a) -> a.getScore() == null ? BigDecimal.valueOf(-1) : a.getScore())
                        .reversed())
                .map(a -> new StudentRow(a.getId(), a.getStudentName(), a.getScore(), a.getStatus(),
                        Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt()))
                .toList();

        return new PlanDetail(summary, buckets, rows);
    }

    // ---------- 内部 ----------

    private List<ExamAttempt> attemptsOf(Long planId) {
        return attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getPlanId, planId));
    }

    private BigDecimal totalOf(ExamPlan p) {
        Paper paper = paperMapper.selectById(p.getPaperId());
        return paper == null ? BigDecimal.ZERO : paper.getTotalScore();
    }

    private PlanStat stat(ExamPlan p, List<ExamAttempt> attempts) {
        Paper paper = paperMapper.selectById(p.getPaperId());
        BigDecimal total = paper == null ? BigDecimal.ZERO : paper.getTotalScore();

        List<ExamAttempt> submitted = attempts.stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()))
                .toList();
        int submittedCount = submitted.size();
        int passCount = (int) submitted.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count();

        BigDecimal avg = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        BigDecimal min = BigDecimal.ZERO;
        if (submittedCount > 0) {
            BigDecimal sum = submitted.stream()
                    .map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(submittedCount), 1, RoundingMode.HALF_UP);
            max = submitted.stream().map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            min = submitted.stream().map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }
        BigDecimal rate = submittedCount == 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(passCount * 100.0 / submittedCount).setScale(1, RoundingMode.HALF_UP);

        return new PlanStat(p.getId(), p.getName(), paper == null ? "-" : paper.getName(),
                p.getStartAt(), p.getEndAt(), total, p.getPassScore(), p.getStatus(),
                attempts.size(), submittedCount, passCount, rate, avg, max, min);
    }
}
