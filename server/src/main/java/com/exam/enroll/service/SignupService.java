package com.exam.enroll.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.enroll.entity.ExamSignup;
import com.exam.enroll.mapper.ExamSignupMapper;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.Paper;
import com.exam.exam.mapper.ExamPlanMapper;
import com.exam.exam.mapper.PaperMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/** 考试报名：学员报名 → 管理员审核 → 审核通过方可开考 */
@Service
@RequiredArgsConstructor
public class SignupService {

    private final ExamSignupMapper signupMapper;
    private final ExamPlanMapper planMapper;
    private final PaperMapper paperMapper;

    public record AvailablePlan(Long planId, String planName, LocalDateTime startAt, LocalDateTime endAt,
                                BigDecimal passScore, BigDecimal totalScore, int durationMin,
                                String planState, String signupStatus, Long signupId) {
    }

    public record SignupRow(Long id, Long planId, String planName, String studentName,
                            String status, String remark, LocalDateTime createdAt, LocalDateTime reviewedAt) {
    }

    /** 学员可报名的考试（已结束的不再展示） */
    public List<AvailablePlan> available(Long userId) {
        List<ExamPlan> plans = planMapper.selectList(Wrappers.<ExamPlan>lambdaQuery()
                .eq(ExamPlan::getStatus, "PUBLISHED")
                .orderByAsc(ExamPlan::getStartAt));
        Map<Long, ExamSignup> mine = new HashMap<>();
        signupMapper.selectList(Wrappers.<ExamSignup>lambdaQuery()
                        .eq(ExamSignup::getUserId, userId))
                .forEach(s -> mine.put(s.getPlanId(), s));

        LocalDateTime now = LocalDateTime.now();
        List<AvailablePlan> out = new ArrayList<>();
        for (ExamPlan p : plans) {
            if (now.isAfter(p.getEndAt())) continue;
            Paper paper = paperMapper.selectById(p.getPaperId());
            ExamSignup s = mine.get(p.getId());
            String state = now.isBefore(p.getStartAt()) ? "UPCOMING" : "ONGOING";
            out.add(new AvailablePlan(p.getId(), p.getName(), p.getStartAt(), p.getEndAt(),
                    p.getPassScore(), paper == null ? BigDecimal.ZERO : paper.getTotalScore(),
                    p.getDurationSec() == null ? 0 : p.getDurationSec() / 60,
                    state, s == null ? "NONE" : s.getStatus(), s == null ? null : s.getId()));
        }
        return out;
    }

    public List<SignupRow> mySignups(Long userId) {
        List<ExamSignup> list = signupMapper.selectList(Wrappers.<ExamSignup>lambdaQuery()
                .eq(ExamSignup::getUserId, userId)
                .orderByDesc(ExamSignup::getCreatedAt));
        return toRows(list);
    }

    @Transactional
    public SignupRow signup(Long planId, Long userId, String studentName) {
        ExamPlan plan = planMapper.selectById(planId);
        if (plan == null || !"PUBLISHED".equals(plan.getStatus())) {
            throw new BizException(404, "考试不存在或未发布");
        }
        if (LocalDateTime.now().isAfter(plan.getEndAt())) {
            throw new BizException("考试已结束，无法报名");
        }

        ExamSignup exist = signupMapper.selectOne(Wrappers.<ExamSignup>lambdaQuery()
                .eq(ExamSignup::getPlanId, planId).eq(ExamSignup::getUserId, userId));

        if (exist != null) {
            if ("REJECTED".equals(exist.getStatus())) {
                exist.setStatus("PENDING");
                exist.setRemark(null);
                exist.setReviewedAt(null);
                exist.setCreatedAt(LocalDateTime.now());
                signupMapper.updateById(exist);
                return toRows(List.of(exist)).get(0);
            }
            throw new BizException("APPROVED".equals(exist.getStatus()) ? "已报名并通过审核" : "已提交报名，请等待审核");
        }

        ExamSignup s = new ExamSignup();
        s.setPlanId(planId);
        s.setUserId(userId);
        s.setStudentName(studentName);
        s.setStatus("PENDING");
        s.setCreatedAt(LocalDateTime.now());
        signupMapper.insert(s);
        return toRows(List.of(s)).get(0);
    }

    @Transactional
    public void cancel(Long id, Long userId) {
        ExamSignup s = signupMapper.selectById(id);
        if (s == null || !Objects.equals(s.getUserId(), userId)) {
            throw new BizException(404, "报名记录不存在");
        }
        if ("APPROVED".equals(s.getStatus())) {
            throw new BizException("已审核通过，不能取消（如需取消请联系管理员）");
        }
        signupMapper.deleteById(id);
    }

    // ---------- 管理端 ----------

    public List<SignupRow> adminList(Long planId, String status) {
        var q = Wrappers.<ExamSignup>lambdaQuery().orderByDesc(ExamSignup::getCreatedAt);
        if (planId != null) q.eq(ExamSignup::getPlanId, planId);
        if (status != null && !status.isBlank()) q.eq(ExamSignup::getStatus, status);
        return toRows(signupMapper.selectList(q));
    }

    @Transactional
    public void review(Long id, String status, String remark) {
        if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
            throw new BizException("审核结果取值非法");
        }
        ExamSignup s = signupMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "报名记录不存在");
        }
        s.setStatus(status);
        s.setRemark(remark);
        s.setReviewedAt(LocalDateTime.now());
        signupMapper.updateById(s);
    }

    /** 开考校验：被拒的报名不允许参加考试 */
    public void assertCanTake(Long planId, Long userId) {
        ExamSignup s = signupMapper.selectOne(Wrappers.<ExamSignup>lambdaQuery()
                .eq(ExamSignup::getPlanId, planId).eq(ExamSignup::getUserId, userId));
        if (s != null && "REJECTED".equals(s.getStatus())) {
            throw new BizException("报名未通过审核，无法参加本次考试");
        }
    }

    private List<SignupRow> toRows(List<ExamSignup> list) {
        Map<Long, ExamPlan> plans = new HashMap<>();
        for (ExamSignup s : list) {
            plans.computeIfAbsent(s.getPlanId(), id -> planMapper.selectById(id));
        }
        List<SignupRow> rows = new ArrayList<>();
        for (ExamSignup s : list) {
            ExamPlan p = plans.get(s.getPlanId());
            rows.add(new SignupRow(s.getId(), s.getPlanId(), p == null ? "-" : p.getName(),
                    s.getStudentName(), s.getStatus(), s.getRemark(), s.getCreatedAt(), s.getReviewedAt()));
        }
        return rows;
    }
}
