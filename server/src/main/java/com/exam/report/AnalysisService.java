package com.exam.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.content.entity.Question;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.entity.AttemptAnswer;
import com.exam.exam.entity.ExamAttempt;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.PaperItem;
import com.exam.exam.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** 成绩分析：按题目 / 题型维度统计正确率，用于评估命题质量 */
@Service
@RequiredArgsConstructor
public class AnalysisService {

    private final ExamPlanMapper planMapper;
    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final PaperItemMapper itemMapper;
    private final QuestionMapper questionMapper;

    public record QuestionStat(Long questionId, int sortNo, String type, String stem,
                               BigDecimal fullScore, int answeredCount, int correctCount,
                               BigDecimal correctRate, BigDecimal avgScore) {
    }

    public record TypeStat(String type, int questionCount, int answeredCount, int correctCount,
                           BigDecimal correctRate) {
    }

    public record AnalysisView(String planName, int attemptCount, List<QuestionStat> questions,
                               List<TypeStat> types) {
    }

    public AnalysisView planAnalysis(Long planId) {
        ExamPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(404, "考试计划不存在");
        }

        List<ExamAttempt> attempts = attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getPlanId, planId)
                .eq(ExamAttempt::getStatus, "SUBMITTED"));
        Set<Long> attemptIds = new HashSet<>();
        attempts.forEach(a -> attemptIds.add(a.getId()));

        List<AttemptAnswer> answers = attemptIds.isEmpty()
                ? List.of()
                : answerMapper.selectList(Wrappers.<AttemptAnswer>lambdaQuery()
                        .in(AttemptAnswer::getAttemptId, attemptIds));

        Map<Long, List<AttemptAnswer>> byQuestion = new HashMap<>();
        Map<Long, List<AttemptAnswer>> byAttempt = new HashMap<>();
        for (AttemptAnswer a : answers) {
            byQuestion.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
            byAttempt.computeIfAbsent(a.getAttemptId(), k -> new ArrayList<>()).add(a);
        }

        List<PaperItem> items = itemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                .eq(PaperItem::getPaperId, plan.getPaperId())
                .orderByAsc(PaperItem::getSortNo));

        List<QuestionStat> rows = new ArrayList<>();
        Map<String, int[]> typeAgg = new LinkedHashMap<>();   // type -> [questionCount, answered, correct]

        for (PaperItem it : items) {
            Question q = questionMapper.selectById(it.getQuestionId());
            if (q == null) continue;

            QuestionStat stat;
            if ("COMPOSITE".equals(q.getType())) {
                stat = compositeStat(q, it, attempts, byAttempt);
            } else {
                List<AttemptAnswer> list = byQuestion.getOrDefault(q.getId(), List.of());
                int answered = (int) list.stream()
                        .filter(x -> x.getAnswer() != null && !x.getAnswer().isBlank())
                        .count();
                int correct = (int) list.stream().filter(x -> Boolean.TRUE.equals(x.getCorrect())).count();
                BigDecimal avg = BigDecimal.ZERO;
                if (!list.isEmpty()) {
                    BigDecimal sum = list.stream()
                            .map(x -> x.getScore() == null ? BigDecimal.ZERO : x.getScore())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    avg = sum.divide(BigDecimal.valueOf(list.size()), 1, RoundingMode.HALF_UP);
                }
                BigDecimal rate = answered == 0
                        ? BigDecimal.ZERO
                        : BigDecimal.valueOf(correct * 100.0 / answered).setScale(1, RoundingMode.HALF_UP);
                stat = new QuestionStat(q.getId(), it.getSortNo(), q.getType(), q.getStem(),
                        it.getScore(), answered, correct, rate, avg);
            }
            rows.add(stat);

            int[] agg = typeAgg.computeIfAbsent(q.getType(), k -> new int[3]);
            agg[0]++;
            agg[1] += stat.answeredCount();
            agg[2] += stat.correctCount();
        }

        List<TypeStat> types = new ArrayList<>();
        typeAgg.forEach((type, agg) -> types.add(new TypeStat(type, agg[0], agg[1], agg[2],
                agg[1] == 0 ? BigDecimal.ZERO
                        : BigDecimal.valueOf(agg[2] * 100.0 / agg[1]).setScale(1, RoundingMode.HALF_UP))));

        return new AnalysisView(plan.getName(), attempts.size(), rows, types);
    }

    /** 题冒题：作答看子题是否作答，对错看题干行（子题全对才算对） */
    private QuestionStat compositeStat(Question q, PaperItem it, List<ExamAttempt> attempts,
                                       Map<Long, List<AttemptAnswer>> byAttempt) {
        Set<Long> subIds = new HashSet<>();
        questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .eq(Question::getParentId, q.getId()))
                .forEach(c -> subIds.add(c.getId()));

        int answered = 0;
        int correct = 0;
        BigDecimal sum = BigDecimal.ZERO;
        int scored = 0;
        for (ExamAttempt at : attempts) {
            List<AttemptAnswer> list = byAttempt.getOrDefault(at.getId(), List.of());
            boolean anyAnswered = list.stream()
                    .filter(x -> subIds.contains(x.getQuestionId()))
                    .anyMatch(x -> x.getAnswer() != null && !x.getAnswer().isBlank());
            if (anyAnswered) answered++;

            AttemptAnswer parentRow = list.stream()
                    .filter(x -> q.getId().equals(x.getQuestionId()))
                    .findFirst().orElse(null);
            if (parentRow != null) {
                if (Boolean.TRUE.equals(parentRow.getCorrect())) correct++;
                sum = sum.add(parentRow.getScore() == null ? BigDecimal.ZERO : parentRow.getScore());
                scored++;
            }
        }
        BigDecimal avg = scored == 0 ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(scored), 1, RoundingMode.HALF_UP);
        BigDecimal rate = answered == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(correct * 100.0 / answered).setScale(1, RoundingMode.HALF_UP);
        return new QuestionStat(q.getId(), it.getSortNo(), q.getType(), q.getStem(),
                it.getScore(), answered, correct, rate, avg);
    }
}
