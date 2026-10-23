package com.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.content.entity.Question;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.entity.AttemptAnswer;
import com.exam.exam.entity.ExamAttempt;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.Paper;
import com.exam.exam.mapper.AttemptAnswerMapper;
import com.exam.exam.mapper.ExamAttemptMapper;
import com.exam.exam.mapper.ExamPlanMapper;
import com.exam.exam.mapper.PaperMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 交卷记录查询（管理端） */
@Service
@RequiredArgsConstructor
public class AttemptQueryService {

    private final ExamAttemptMapper attemptMapper;
    private final ExamPlanMapper planMapper;
    private final PaperMapper paperMapper;
    private final AttemptAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;

    public record AttemptRow(Long attemptId, Long planId, String planName, String studentName, String status,
                             BigDecimal score, BigDecimal totalScore, boolean passed,
                             LocalDateTime startedAt, LocalDateTime submittedAt,
                             int answerCount, int essayCount, int essayMarked, int switchCount) {
    }

    public List<AttemptRow> list(Long planId, String status, String keyword) {
        var q = Wrappers.<ExamAttempt>lambdaQuery().orderByDesc(ExamAttempt::getStartedAt);
        if (planId != null) q.eq(ExamAttempt::getPlanId, planId);
        if (StringUtils.hasText(status)) q.eq(ExamAttempt::getStatus, status);
        if (StringUtils.hasText(keyword)) q.like(ExamAttempt::getStudentName, keyword);
        List<ExamAttempt> attempts = attemptMapper.selectList(q);

        Map<Long, ExamPlan> planCache = new HashMap<>();
        Map<Long, BigDecimal> totalCache = new HashMap<>();

        List<AttemptRow> rows = new ArrayList<>();
        for (ExamAttempt a : attempts) {
            ExamPlan plan = planCache.computeIfAbsent(a.getPlanId(), planMapper::selectById);
            BigDecimal total = BigDecimal.ZERO;
            if (plan != null) {
                total = totalCache.computeIfAbsent(plan.getPaperId(), pid -> {
                    Paper p = paperMapper.selectById(pid);
                    return p == null ? BigDecimal.ZERO : p.getTotalScore();
                });
            }

            List<AttemptAnswer> answers = answerMapper.selectList(Wrappers.<AttemptAnswer>lambdaQuery()
                    .eq(AttemptAnswer::getAttemptId, a.getId()));
            int essayCount = 0;
            int essayMarked = 0;
            for (AttemptAnswer ans : answers) {
                Question ques = questionMapper.selectById(ans.getQuestionId());
                if (ques != null && "ESSAY".equals(ques.getType())) {
                    essayCount++;
                    if (Boolean.TRUE.equals(ans.getManual())) essayMarked++;
                }
            }

            rows.add(new AttemptRow(a.getId(), a.getPlanId(), plan == null ? "-" : plan.getName(),
                    a.getStudentName(), a.getStatus(), a.getScore(), total,
                    Boolean.TRUE.equals(a.getPassed()), a.getStartedAt(), a.getSubmittedAt(),
                    answers.size(), essayCount, essayMarked,
                    a.getSwitchCount() == null ? 0 : a.getSwitchCount()));
        }
        return rows;
    }
}
