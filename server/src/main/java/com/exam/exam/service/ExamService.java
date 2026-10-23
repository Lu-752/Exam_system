package com.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.content.dto.Option;
import com.exam.content.entity.Question;
import com.exam.content.grading.GradeRequest;
import com.exam.content.grading.GradingEngine;
import com.exam.content.grading.QuestionType;
import com.exam.content.mapper.QuestionMapper;
import com.exam.content.service.ContentService;
import com.exam.enroll.service.SignupService;
import com.exam.exam.dto.ExamDtos.*;
import com.exam.exam.entity.*;
import com.exam.exam.mapper.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExamService {

    private final PaperMapper paperMapper;
    private final PaperItemMapper paperItemMapper;
    private final ExamPlanMapper planMapper;
    private final ExamAttemptMapper attemptMapper;
    private final AttemptAnswerMapper answerMapper;
    private final QuestionMapper questionMapper;
    private final SignupService signupService;
    private final ContentService contentService;
    private final ObjectMapper json;

    // ==================== 组卷 ====================

    @Transactional
    public PaperView createPaper(PaperUpsert req) {
        if (!StringUtils.hasText(req.name())) {
            throw new BizException("试卷名称不能为空");
        }
        if (req.items() == null || req.items().isEmpty()) {
            throw new BizException("请至少选择 1 道试题");
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PaperItemReq it : req.items()) {
            total = total.add(it.score() == null ? BigDecimal.valueOf(5) : it.score());
        }

        Paper paper = new Paper();
        paper.setName(req.name().trim());
        paper.setBankId(req.bankId());
        paper.setTotalScore(total);
        paper.setPassScore(req.passScore() == null ? BigDecimal.valueOf(60) : req.passScore());
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int sort = 1;
        for (PaperItemReq it : req.items()) {
            contentService.requireQuestion(it.questionId());
            PaperItem item = new PaperItem();
            item.setPaperId(paper.getId());
            item.setQuestionId(it.questionId());
            item.setSortNo(sort++);
            item.setScore(it.score() == null ? BigDecimal.valueOf(5) : it.score());
            paperItemMapper.insert(item);
        }
        return new PaperView(paper.getId(), paper.getName(), total, paper.getPassScore(),
                req.items().size(), paper.getCreatedAt());
    }

    /** 自动组卷：按题型配比从题库随机抽题 */
    @Transactional
    public PaperView autoCreatePaper(AutoPaperReq req) {
        if (!StringUtils.hasText(req.name())) {
            throw new BizException("试卷名称不能为空");
        }
        if (req.bankId() == null) {
            throw new BizException("请选择题库");
        }
        if (req.rules() == null || req.rules().isEmpty()) {
            throw new BizException("请配置抽题规则");
        }

        List<Question> picked = new ArrayList<>();
        Map<Long, BigDecimal> scoreMap = new HashMap<>();
        StringBuilder gap = new StringBuilder();

        for (AutoRule rule : req.rules()) {
            if (rule.count() <= 0) continue;
            List<Question> pool = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                    .isNull(Question::getParentId)
                    .eq(Question::getBankId, req.bankId())
                    .eq(Question::getType, rule.type()));
            if (pool.size() < rule.count()) {
                gap.append(rule.type()).append(" 需要 ").append(rule.count())
                        .append(" 题，题库仅 ").append(pool.size()).append(" 题；");
                continue;
            }
            Collections.shuffle(pool);
            for (Question q : pool.subList(0, rule.count())) {
                picked.add(q);
                scoreMap.put(q.getId(), rule.score() == null ? BigDecimal.TEN : rule.score());
            }
        }
        if (gap.length() > 0) {
            throw new BizException("题库题目不足：" + gap);
        }
        if (picked.isEmpty()) {
            throw new BizException("抽题结果为空");
        }

        Paper paper = new Paper();
        paper.setName(req.name().trim());
        paper.setBankId(req.bankId());
        BigDecimal total = picked.stream()
                .map(q -> scoreMap.getOrDefault(q.getId(), BigDecimal.TEN))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paper.setTotalScore(total);
        paper.setPassScore(req.passScore() == null ? BigDecimal.valueOf(60) : req.passScore());
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int sort = 1;
        for (Question q : picked) {
            PaperItem item = new PaperItem();
            item.setPaperId(paper.getId());
            item.setQuestionId(q.getId());
            item.setSortNo(sort++);
            item.setScore(scoreMap.getOrDefault(q.getId(), BigDecimal.TEN));
            paperItemMapper.insert(item);
        }
        return new PaperView(paper.getId(), paper.getName(), total, paper.getPassScore(),
                picked.size(), paper.getCreatedAt());
    }

    public List<PaperView> listPapers() {        List<Paper> papers = paperMapper.selectList(Wrappers.<Paper>lambdaQuery().orderByDesc(Paper::getId));
        return papers.stream().map(p -> {
            Long cnt = paperItemMapper.selectCount(
                    Wrappers.<PaperItem>lambdaQuery().eq(PaperItem::getPaperId, p.getId()));
            return new PaperView(p.getId(), p.getName(), p.getTotalScore(), p.getPassScore(),
                    cnt == null ? 0 : cnt.intValue(), p.getCreatedAt());
        }).toList();
    }

    // ==================== 考试计划 ====================

    @Transactional
    public PlanView createPlan(PlanUpsert req) {
        if (!StringUtils.hasText(req.name())) {
            throw new BizException("考试名称不能为空");
        }
        if (req.paperId() == null || paperMapper.selectById(req.paperId()) == null) {
            throw new BizException("请选择试卷");
        }
        if (req.startAt() == null || req.endAt() == null || !req.endAt().isAfter(req.startAt())) {
            throw new BizException("结束时间必须晚于开始时间");
        }
        Paper paper = paperMapper.selectById(req.paperId());

        ExamPlan plan = new ExamPlan();
        plan.setName(req.name().trim());
        plan.setPaperId(req.paperId());
        plan.setStartAt(req.startAt());
        plan.setEndAt(req.endAt());
        plan.setDurationSec(req.durationSec() == null ? 1800 : req.durationSec());
        plan.setPassScore(req.passScore() == null ? paper.getPassScore() : req.passScore());
        plan.setMaxAttempts(req.maxAttempts() == null ? 1 : req.maxAttempts());
        plan.setStatus("PUBLISHED");
        plan.setScorePublished(false);
        plan.setCreatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        return toPlanView(plan, paper, null);
    }

    /** 成绩发布 / 撤回（阅卷完成后由管理员操作） */
    @Transactional
    public void publishScores(Long planId, boolean published) {
        ExamPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new BizException(404, "考试不存在");
        }
        plan.setScorePublished(published);
        planMapper.updateById(plan);
    }

    public List<PlanView> listPlans() {
        List<ExamPlan> plans = planMapper.selectList(Wrappers.<ExamPlan>lambdaQuery().orderByDesc(ExamPlan::getId));
        List<PlanView> out = new ArrayList<>();
        for (ExamPlan p : plans) {
            out.add(toPlanView(p, paperMapper.selectById(p.getPaperId()), null));
        }
        return out;
    }

    /** 学员视角：已发布的考试 + 我的作答情况 */
    public List<PlanView> listForStudent(Long userId) {
        List<ExamPlan> plans = planMapper.selectList(Wrappers.<ExamPlan>lambdaQuery()
                .eq(ExamPlan::getStatus, "PUBLISHED")
                .orderByDesc(ExamPlan::getStartAt));
        List<PlanView> out = new ArrayList<>();
        for (ExamPlan p : plans) {
            ExamAttempt mine = attemptMapper.selectOne(Wrappers.<ExamAttempt>lambdaQuery()
                    .eq(ExamAttempt::getPlanId, p.getId())
                    .eq(ExamAttempt::getUserId, userId));
            out.add(toPlanView(p, paperMapper.selectById(p.getPaperId()), mine));
        }
        return out;
    }

    private PlanView toPlanView(ExamPlan p, Paper paper, ExamAttempt mine) {
        Long qCount = paperItemMapper.selectCount(
                Wrappers.<PaperItem>lambdaQuery().eq(PaperItem::getPaperId, p.getPaperId()));
        String state = planState(p);
        boolean published = Boolean.TRUE.equals(p.getScorePublished());
        // 成绩未发布时，即便已交卷也不把分数下发给学员
        BigDecimal myScore = mine == null ? null : (published ? mine.getScore() : null);
        Boolean myPassed = mine == null ? null : (published ? mine.getPassed() : null);
        return new PlanView(p.getId(), p.getName(), p.getPaperId(), paper == null ? "-" : paper.getName(),
                p.getStartAt(), p.getEndAt(), p.getDurationSec(), p.getPassScore(),
                paper == null ? BigDecimal.ZERO : paper.getTotalScore(),
                qCount == null ? 0 : qCount.intValue(), state,
                mine == null ? null : mine.getId(),
                mine == null ? null : mine.getStatus(),
                myScore, myPassed, published);
    }

    private String planState(ExamPlan p) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(p.getStartAt())) return "UPCOMING";
        if (now.isAfter(p.getEndAt())) return "FINISHED";
        return "ONGOING";
    }

    // ==================== 考试执行 ====================

    @Transactional
    public AttemptStart start(Long planId, Long userId, String userName) {
        ExamPlan plan = planMapper.selectById(planId);
        if (plan == null || !"PUBLISHED".equals(plan.getStatus())) {
            throw new BizException(404, "考试不存在或未发布");
        }
        String state = planState(plan);
        if ("UPCOMING".equals(state)) {
            throw new BizException("考试尚未开始");
        }
        if ("FINISHED".equals(state)) {
            throw new BizException("考试已结束");
        }
        signupService.assertCanTake(planId, userId);

        ExamAttempt exist = attemptMapper.selectOne(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getPlanId, planId).eq(ExamAttempt::getUserId, userId));
        if (exist != null && "SUBMITTED".equals(exist.getStatus())) {
            throw new BizException("本场考试已交卷，不能重复作答");
        }

        ExamAttempt attempt = exist;
        if (attempt == null) {
            attempt = new ExamAttempt();
            attempt.setPlanId(planId);
            attempt.setUserId(userId);
            attempt.setStudentName(userName);
            attempt.setStartedAt(LocalDateTime.now());
            attempt.setDeadlineAt(deadline(plan, LocalDateTime.now()));
            attempt.setStatus("IN_PROGRESS");
            attemptMapper.insert(attempt);
        }

        List<PaperItem> items = paperItemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                .eq(PaperItem::getPaperId, plan.getPaperId())
                .orderByAsc(PaperItem::getSortNo));

        List<ExamQuestion> questions = new ArrayList<>();
        for (PaperItem it : items) {
            Question q = questionMapper.selectById(it.getQuestionId());
            if (q == null) continue;
            questions.add(toExamQuestion(q, it.getScore(), it.getSortNo()));
        }

        long remaining = Math.max(0, Duration.between(LocalDateTime.now(), attempt.getDeadlineAt()).getSeconds());
        Paper paper = paperMapper.selectById(plan.getPaperId());
        return new AttemptStart(attempt.getId(), plan.getId(), plan.getName(), plan.getPassScore(),
                paper == null ? BigDecimal.ZERO : paper.getTotalScore(), remaining, questions);
    }

    private LocalDateTime deadline(ExamPlan plan, LocalDateTime start) {
        LocalDateTime byDuration = start.plusSeconds(plan.getDurationSec());
        return byDuration.isBefore(plan.getEndAt()) ? byDuration : plan.getEndAt();
    }

    @Transactional
    public ResultView submit(Long attemptId, Long userId, SubmitReq req) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(404, "答卷不存在");
        }
        if (!attempt.getUserId().equals(userId)) {
            throw new BizException(403, "无权提交他人答卷");
        }
        if ("SUBMITTED".equals(attempt.getStatus())) {
            throw new BizException("答卷已提交，请勿重复交卷");
        }

        ExamPlan plan = planMapper.selectById(attempt.getPlanId());
        List<PaperItem> items = paperItemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                .eq(PaperItem::getPaperId, plan.getPaperId())
                .orderByAsc(PaperItem::getSortNo));

        Map<Long, String> submitted = new HashMap<>();
        if (req != null && req.answers() != null) {
            for (AnswerItem a : req.answers()) {
                submitted.put(a.questionId(), a.answer());
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        int correctCount = 0;
        int essayCount = 0;
        List<ResultDetail> details = new ArrayList<>();

        for (PaperItem it : items) {
            Question q = questionMapper.selectById(it.getQuestionId());
            if (q == null) continue;

            // 题冒题：逐个子题判分后累加，封顶本题满分
            if (QuestionType.COMPOSITE.name().equals(q.getType())) {
                List<Question> subs = contentService.childrenOf(q.getId());
                BigDecimal sum = BigDecimal.ZERO;
                int subCorrect = 0;
                int answered = 0;
                List<ResultDetail> childDetails = new ArrayList<>();
                for (Question c : subs) {
                    String ca = submitted.getOrDefault(c.getId(), "");
                    QuestionType ct = parseType(c.getType());
                    BigDecimal cFull = c.getScore() == null ? BigDecimal.valueOf(5) : c.getScore();
                    GradeRequest.GradeResult gr = GradingEngine.grade(new GradeRequest(
                            ct, correctList(ct, c.getAnswer()), splitSelected(ct, ca), ca, cFull.doubleValue()));
                    if (ct == QuestionType.ESSAY) essayCount++;
                    if (gr.correct()) subCorrect++;
                    if (StringUtils.hasText(ca)) answered++;
                    sum = sum.add(BigDecimal.valueOf(gr.score()));
                    saveAnswer(attemptId, c.getId(), ca, gr.correct(), BigDecimal.valueOf(gr.score()));
                    childDetails.add(new ResultDetail(c.getId(), c.getType(), c.getStem(), ca,
                            c.getAnswer(), gr.correct(), BigDecimal.valueOf(gr.score()), cFull,
                            c.getAnalysis(), null));
                }
                BigDecimal finalScore = sum.min(it.getScore());
                boolean allOk = !subs.isEmpty() && subCorrect == subs.size();
                if (allOk) correctCount++;
                total = total.add(finalScore);
                saveAnswer(attemptId, q.getId(), "", allOk, finalScore);
                details.add(new ResultDetail(q.getId(), q.getType(), q.getStem(), "",
                        null, allOk, finalScore, it.getScore(), q.getAnalysis(), childDetails));
                continue;
            }

            String myAnswer = submitted.getOrDefault(q.getId(), "");
            QuestionType type = parseType(q.getType());
            GradeRequest.GradeResult gr = GradingEngine.grade(new GradeRequest(
                    type, correctList(type, q.getAnswer()), splitSelected(type, myAnswer), myAnswer, it.getScore().doubleValue()));

            if (type == QuestionType.ESSAY) {
                essayCount++;
            }
            if (gr.correct()) {
                correctCount++;
            }
            total = total.add(BigDecimal.valueOf(gr.score()));

            saveAnswer(attemptId, q.getId(), myAnswer, gr.correct(), BigDecimal.valueOf(gr.score()));

            details.add(new ResultDetail(q.getId(), q.getType(), q.getStem(), myAnswer, q.getAnswer(),
                    gr.correct(), BigDecimal.valueOf(gr.score()), it.getScore(), q.getAnalysis(), null));
        }

        boolean passed = total.compareTo(plan.getPassScore()) >= 0;
        attempt.setStatus("SUBMITTED");
        attempt.setSubmittedAt(LocalDateTime.now());
        attempt.setScore(total);
        attempt.setPassed(passed);
        attemptMapper.updateById(attempt);

        BigDecimal paperTotal = paperMapper.selectById(plan.getPaperId()).getTotalScore();
        // 成绩未发布：交卷只回执「已交卷」，不下发分数与解析
        if (!Boolean.TRUE.equals(plan.getScorePublished())) {
            return new ResultView(attemptId, plan.getName(), null, paperTotal, false, 0, items.size(),
                    List.of(), false);
        }
        return new ResultView(attemptId, plan.getName(), total, paperTotal,
                passed, correctCount, items.size(), details, true);
    }

    /** 切屏 / 失焦上报：累计次数，供监考页展示与预警 */
    @Transactional
    public EventAck reportEvent(Long attemptId, Long userId, EventReq req) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(404, "答卷不存在");
        }
        if (!attempt.getUserId().equals(userId)) {
            throw new BizException(403, "无权操作他人答卷");
        }
        int current = attempt.getSwitchCount() == null ? 0 : attempt.getSwitchCount();
        // 已交卷后不再累计，直接回执当前值（避免前端晚到的事件报错）
        if ("SUBMITTED".equals(attempt.getStatus())) {
            return new EventAck(current, current >= SWITCH_WARN, attempt.getLastSwitchAt());
        }

        // 单次上报最多累计 20，避免刷接口灌水
        int delta = req == null || req.count() == null ? 1 : Math.max(1, Math.min(req.count(), 20));
        int total = current + delta;
        attempt.setSwitchCount(total);
        attempt.setLastSwitchAt(LocalDateTime.now());
        attemptMapper.updateById(attempt);
        return new EventAck(total, total >= SWITCH_WARN, attempt.getLastSwitchAt());
    }

    /** 切屏预警阈值：达到该次数即标记为异常 */
    public static final int SWITCH_WARN = 3;

    /** 交卷后查看成绩（学员） */
    public ResultView result(Long attemptId, Long userId) {
        ExamAttempt attempt = attemptMapper.selectById(attemptId);
        if (attempt == null) {
            throw new BizException(404, "答卷不存在");
        }
        if (!attempt.getUserId().equals(userId)) {
            throw new BizException(403, "无权查看他人答卷");
        }
        if (!"SUBMITTED".equals(attempt.getStatus())) {
            throw new BizException("尚未交卷");
        }
        ExamPlan plan = planMapper.selectById(attempt.getPlanId());
        if (plan != null && !Boolean.TRUE.equals(plan.getScorePublished())) {
            throw new BizException("成绩尚未发布，请等待管理员发布后查看");
        }
        List<AttemptAnswer> rows = answerMapper.selectList(Wrappers.<AttemptAnswer>lambdaQuery()
                .eq(AttemptAnswer::getAttemptId, attemptId));

        Map<Long, AttemptAnswer> byQ = new HashMap<>();
        rows.forEach(r -> byQ.put(r.getQuestionId(), r));

        List<PaperItem> items = paperItemMapper.selectList(Wrappers.<PaperItem>lambdaQuery()
                .eq(PaperItem::getPaperId, plan.getPaperId())
                .orderByAsc(PaperItem::getSortNo));

        List<ResultDetail> details = new ArrayList<>();
        int correctCount = 0;
        for (PaperItem it : items) {
            Question q = questionMapper.selectById(it.getQuestionId());
            AttemptAnswer a = byQ.get(it.getQuestionId());
            if (q == null) continue;
            boolean ok = a != null && Boolean.TRUE.equals(a.getCorrect());
            if (ok) correctCount++;

            if (QuestionType.COMPOSITE.name().equals(q.getType())) {
                List<ResultDetail> childDetails = new ArrayList<>();
                for (Question c : contentService.childrenOf(q.getId())) {
                    AttemptAnswer ca = byQ.get(c.getId());
                    BigDecimal cFull = c.getScore() == null ? BigDecimal.valueOf(5) : c.getScore();
                    childDetails.add(new ResultDetail(c.getId(), c.getType(), c.getStem(),
                            ca == null ? "" : ca.getAnswer(), c.getAnswer(),
                            ca != null && Boolean.TRUE.equals(ca.getCorrect()),
                            ca == null ? BigDecimal.ZERO : ca.getScore(), cFull, c.getAnalysis(), null));
                }
                details.add(new ResultDetail(q.getId(), q.getType(), q.getStem(), "", null, ok,
                        a == null ? BigDecimal.ZERO : a.getScore(), it.getScore(), q.getAnalysis(), childDetails));
                continue;
            }

            details.add(new ResultDetail(q.getId(), q.getType(), q.getStem(),
                    a == null ? "" : a.getAnswer(), q.getAnswer(), ok,
                    a == null ? BigDecimal.ZERO : a.getScore(), it.getScore(), q.getAnalysis(), null));
        }

        Paper paper = paperMapper.selectById(plan.getPaperId());
        return new ResultView(attemptId, plan.getName(), attempt.getScore(),
                paper == null ? BigDecimal.ZERO : paper.getTotalScore(),
                Boolean.TRUE.equals(attempt.getPassed()), correctCount, items.size(), details, true);
    }

    // ==================== 工具 ====================

    private ExamQuestion toExamQuestion(Question q, BigDecimal score, int sortNo) {
        List<ExamQuestion> children = null;
        if (QuestionType.COMPOSITE.name().equals(q.getType())) {
            children = new ArrayList<>();
            int i = 0;
            for (Question c : contentService.childrenOf(q.getId())) {
                children.add(new ExamQuestion(c.getId(), c.getType(), c.getStem(),
                        readOptions(c.getOptions()),
                        c.getScore() == null ? BigDecimal.valueOf(5) : c.getScore(), ++i, null));
            }
        }
        return new ExamQuestion(q.getId(), q.getType(), q.getStem(), readOptions(q.getOptions()),
                score, sortNo, children);
    }

    private void saveAnswer(Long attemptId, Long questionId, String answer, boolean correct, BigDecimal score) {
        AttemptAnswer row = new AttemptAnswer();
        row.setAttemptId(attemptId);
        row.setQuestionId(questionId);
        row.setAnswer(answer);
        row.setCorrect(correct);
        row.setScore(score);
        AttemptAnswer exist = answerMapper.selectOne(Wrappers.<AttemptAnswer>lambdaQuery()
                .eq(AttemptAnswer::getAttemptId, attemptId)
                .eq(AttemptAnswer::getQuestionId, questionId));
        if (exist == null) {
            answerMapper.insert(row);
        } else {
            row.setId(exist.getId());
            answerMapper.updateById(row);
        }
    }

    private QuestionType parseType(String type) {
        try {
            return QuestionType.valueOf(type);
        } catch (Exception e) {
            return QuestionType.ESSAY;
        }
    }

    /** 正确答案 → 判分引擎入参：多选用逗号、填空用竖线，其余单个 */
    private List<String> correctList(QuestionType type, String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        return switch (type) {
            case MC -> Arrays.stream(answer.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            case BLANK -> Arrays.stream(answer.split("\\|")).map(String::trim).toList();
            default -> List.of(answer.trim());
        };
    }

    private List<String> splitSelected(QuestionType type, String answer) {
        if (!StringUtils.hasText(answer)) {
            return List.of();
        }
        return switch (type) {
            case MC -> Arrays.stream(answer.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            default -> List.of(answer);
        };
    }

    private List<Option> readOptions(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<List<Option>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
