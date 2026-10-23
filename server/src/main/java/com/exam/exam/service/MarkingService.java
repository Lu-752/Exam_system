package com.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.content.entity.Question;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.dto.MarkingDtos.*;
import com.exam.exam.entity.*;
import com.exam.exam.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MarkingService {

    private final ExamAttemptMapper attemptMapper;
    private final ExamPlanMapper planMapper;
    private final PaperMapper paperMapper;
    private final PaperItemMapper itemMapper;
    private final AttemptAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;

    /** 待批阅：含主观题且尚未全部人工评分的已交卷答卷 */
    public List<PendingItem> pending() {
        List<ExamAttempt> attempts = attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getStatus, "SUBMITTED")
                .orderByDesc(ExamAttempt::getSubmittedAt));

        List<PendingItem> out = new ArrayList<>();
        for (ExamAttempt a : attempts) {
            List<AttemptAnswer> answers = answers(a.getId());
            List<AttemptAnswer> essays = answers.stream()
                    .filter(x -> "ESSAY".equals(typeOf(x.getQuestionId())))
                    .toList();
            if (essays.isEmpty()) continue;

            long marked = essays.stream().filter(x -> Boolean.TRUE.equals(x.getManual())).count();
            ExamPlan plan = planMapper.selectById(a.getPlanId());
            Paper paper = plan == null ? null : paperMapper.selectById(plan.getPaperId());
            BigDecimal total = paper == null ? BigDecimal.ZERO : paper.getTotalScore();

            out.add(new PendingItem(a.getId(), a.getPlanId(), plan == null ? "-" : plan.getName(),
                    a.getStudentName(), a.getSubmittedAt(), a.getScore(), total,
                    Boolean.TRUE.equals(a.getPassed()), essays.size(), (int) marked));
        }
        return out;
    }

    public AttemptReview review(Long attemptId) {
        ExamAttempt a = attemptMapper.selectById(attemptId);
        if (a == null) {
            throw new BizException(404, "答卷不存在");
        }
        ExamPlan plan = planMapper.selectById(a.getPlanId());
        Paper paper = plan == null ? null : paperMapper.selectById(plan.getPaperId());

        Map<Long, BigDecimal> fullScores = new HashMap<>();
        if (paper != null) {
            itemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                            .eq(PaperItem::getPaperId, paper.getId()))
                    .forEach(i -> fullScores.put(i.getQuestionId(), i.getScore()));
        }

        List<ReviewItem> items = new ArrayList<>();
        for (AttemptAnswer ans : answers(attemptId)) {
            Question q = questionMapper.selectById(ans.getQuestionId());
            if (q == null) continue;
            // 题冒题本身不评分（分数由子题累加），不进入阅卷列表
            if ("COMPOSITE".equals(q.getType())) continue;

            String stem = q.getStem();
            BigDecimal full = fullScores.get(q.getId());
            if (q.getParentId() != null) {
                Question parent = questionMapper.selectById(q.getParentId());
                if (parent != null) {
                    stem = "【材料】" + parent.getStem() + "\n" + stem;
                }
                if (full == null) {
                    full = q.getScore() == null ? BigDecimal.TEN : q.getScore();
                }
            }
            items.add(new ReviewItem(ans.getId(), q.getId(), q.getType(), stem,
                    ans.getAnswer(), q.getAnswer(), q.getAnalysis(),
                    full == null ? BigDecimal.TEN : full,
                    ans.getScore(), ans.getComment(), ans.getManual()));
        }

        return new AttemptReview(a.getId(), plan == null ? "-" : plan.getName(), a.getStudentName(),
                a.getScore(), paper == null ? BigDecimal.ZERO : paper.getTotalScore(),
                Boolean.TRUE.equals(a.getPassed()), a.getSubmittedAt(), items);
    }

    /** 人工评分：写分数与评语，并重算答卷总分 */
    @Transactional
    public AttemptReview save(Long answerId, BigDecimal score, String comment) {
        AttemptAnswer ans = answerMapper.selectById(answerId);
        if (ans == null) {
            throw new BizException(404, "答题记录不存在");
        }
        if (score == null || score.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException("分数不能为负");
        }

        ExamAttempt attempt = attemptMapper.selectById(ans.getAttemptId());
        ExamPlan plan = attempt == null ? null : planMapper.selectById(attempt.getPlanId());
        Paper paper = plan == null ? null : paperMapper.selectById(plan.getPaperId());
        Question question = questionMapper.selectById(ans.getQuestionId());

        BigDecimal full = null;
        if (paper != null) {
            PaperItem item = itemMapper.selectOne(Wrappers.<PaperItem>lambdaQuery()
                    .eq(PaperItem::getPaperId, paper.getId())
                    .eq(PaperItem::getQuestionId, ans.getQuestionId()));
            if (item != null) full = item.getScore();
        }
        // 子题不在试卷明细中，按子题自身分值判满分
        if (full == null && question != null && question.getScore() != null) {
            full = question.getScore();
        }
        if (full == null) full = BigDecimal.TEN;
        if (score.compareTo(full) > 0) {
            throw new BizException("分数不能超过本题满分 " + full);
        }

        ans.setScore(score);
        ans.setComment(comment);
        ans.setManual(true);
        ans.setCorrect(score.compareTo(full) >= 0);
        answerMapper.updateById(ans);

        // 重算总分（题冒题先按子题累加，再汇总到答卷）
        recompute(ans.getAttemptId());
        return review(ans.getAttemptId());
    }

    // ---------- 监考 ----------

    public List<ProctorRow> proctorRows(Long planId) {
        List<ExamAttempt> attempts = attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getPlanId, planId)
                .orderByAsc(ExamAttempt::getStartedAt));

        List<ProctorRow> out = new ArrayList<>();
        for (ExamAttempt a : attempts) {
            int answered = (int) answers(a.getId()).stream()
                    .filter(x -> x.getAnswer() != null && !x.getAnswer().isBlank())
                    .count();
            long remaining = "IN_PROGRESS".equals(a.getStatus())
                    ? Math.max(0, Duration.between(LocalDateTime.now(), a.getDeadlineAt()).getSeconds())
                    : 0;
            out.add(row(a, remaining, answered));
        }
        return out;
    }

    private ProctorRow row(ExamAttempt a, long remaining, int answered) {
        int sw = a.getSwitchCount() == null ? 0 : a.getSwitchCount();
        return new ProctorRow(a.getId(), a.getStudentName(), a.getStatus(),
                a.getStartedAt(), remaining, answered, a.getScore(),
                sw, a.getLastSwitchAt(), sw >= ExamService.SWITCH_WARN);
    }

    /** 强制交卷：按已保存的作答结算 */
    @Transactional
    public ProctorRow forceSubmit(Long attemptId) {
        ExamAttempt a = attemptMapper.selectById(attemptId);
        if (a == null) {
            throw new BizException(404, "答卷不存在");
        }
        if ("SUBMITTED".equals(a.getStatus())) {
            throw new BizException("该考生已交卷");
        }
        recompute(attemptId);
        ExamAttempt latest = attemptMapper.selectById(attemptId);
        BigDecimal total = latest == null || latest.getScore() == null ? BigDecimal.ZERO : latest.getScore();

        a.setStatus("SUBMITTED");
        a.setSubmittedAt(LocalDateTime.now());
        a.setScore(total);
        ExamPlan plan = planMapper.selectById(a.getPlanId());
        a.setPassed(plan != null && total.compareTo(plan.getPassScore()) >= 0);
        attemptMapper.updateById(a);

        return new ProctorRow(a.getId(), a.getStudentName(), a.getStatus(),
                a.getStartedAt(), 0, answers(attemptId).size(), total,
                a.getSwitchCount() == null ? 0 : a.getSwitchCount(), a.getLastSwitchAt(),
                (a.getSwitchCount() == null ? 0 : a.getSwitchCount()) >= ExamService.SWITCH_WARN);
    }

    // ---------- 内部 ----------

    /** 重算答卷总分：子题分数先累加成题冒题得分，再汇总顶层题目 */
    private void recompute(Long attemptId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) return;
        ExamPlan plan = planMapper.selectById(attempt.getPlanId());
        Paper paper = plan == null ? null : paperMapper.selectById(plan.getPaperId());

        Map<Long, BigDecimal> paperFull = new HashMap<>();
        if (paper != null) {
            itemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                            .eq(PaperItem::getPaperId, paper.getId()))
                    .forEach(i -> paperFull.put(i.getQuestionId(), i.getScore()));
        }

        List<AttemptAnswer> all = answers(attemptId);
        Map<Long, AttemptAnswer> byQ = new HashMap<>();
        all.forEach(x -> byQ.put(x.getQuestionId(), x));

        BigDecimal total = BigDecimal.ZERO;
        for (AttemptAnswer a : all) {
            Question q = questionMapper.selectById(a.getQuestionId());
            if (q == null || q.getParentId() != null) continue;   // 子题不单独计入总分

            BigDecimal score = a.getScore() == null ? BigDecimal.ZERO : a.getScore();
            if ("COMPOSITE".equals(q.getType())) {
                List<Question> subs = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                        .eq(Question::getParentId, q.getId()));
                BigDecimal sum = BigDecimal.ZERO;
                boolean allCorrect = !subs.isEmpty();
                for (Question c : subs) {
                    AttemptAnswer ca = byQ.get(c.getId());
                    if (ca == null) {
                        allCorrect = false;
                        continue;
                    }
                    if (ca.getScore() != null) sum = sum.add(ca.getScore());
                    if (!Boolean.TRUE.equals(ca.getCorrect())) allCorrect = false;
                }
                BigDecimal full = paperFull.get(q.getId());
                if (full == null) full = q.getScore();
                if (full != null && sum.compareTo(full) > 0) sum = full;
                if (sum.compareTo(score) != 0) {
                    a.setScore(sum);
                    answerMapper.updateById(a);
                }
                if (!Objects.equals(a.getCorrect(), allCorrect)) {
                    a.setCorrect(allCorrect);
                    answerMapper.updateById(a);
                }
                score = sum;
            }
            total = total.add(score);
        }

        attempt.setScore(total);
        attempt.setPassed(plan != null && total.compareTo(plan.getPassScore()) >= 0);
        attemptMapper.updateById(attempt);
    }

    private List<AttemptAnswer> answers(Long attemptId) {
        return answerMapper.selectList(Wrappers.<AttemptAnswer>lambdaQuery()
                .eq(AttemptAnswer::getAttemptId, attemptId));
    }

    private String typeOf(Long questionId) {
        Question q = questionMapper.selectById(questionId);
        return q == null ? "" : q.getType();
    }
}
