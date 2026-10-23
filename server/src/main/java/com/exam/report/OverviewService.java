package com.exam.report;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.entity.ExamAttempt;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.Paper;
import com.exam.exam.mapper.ExamAttemptMapper;
import com.exam.exam.mapper.ExamPlanMapper;
import com.exam.exam.mapper.PaperMapper;
import com.exam.system.entity.SysStudent;
import com.exam.system.mapper.SysStudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 首页概览：真实聚合统计 */
@Service
@RequiredArgsConstructor
public class OverviewService {

    private final SysStudentMapper studentMapper;
    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;
    private final ExamPlanMapper planMapper;
    private final ExamAttemptMapper attemptMapper;

    public record Metric(String label, String value) {
    }

    public List<Metric> metrics() {
        long students = studentMapper.selectCount(Wrappers.<SysStudent>lambdaQuery());
        long banks = bankMapper.selectCount(Wrappers.<QuestionBank>lambdaQuery());
        long questions = questionMapper.selectCount(Wrappers.<Question>lambdaQuery());
        long papers = paperMapper.selectCount(Wrappers.<Paper>lambdaQuery());
        long plans = planMapper.selectCount(Wrappers.<ExamPlan>lambdaQuery());
        long attempts = attemptMapper.selectCount(Wrappers.<ExamAttempt>lambdaQuery());

        LocalDateTime now = LocalDateTime.now();
        long today = planMapper.selectCount(Wrappers.<ExamPlan>lambdaQuery()
                .le(ExamPlan::getStartAt, now)
                .ge(ExamPlan::getEndAt, now));

        List<ExamAttempt> submitted = attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getStatus, "SUBMITTED"));

        long submittedCount = submitted.size();
        BigDecimal avg = BigDecimal.ZERO;
        BigDecimal rate = BigDecimal.ZERO;
        if (submittedCount > 0) {
            BigDecimal sum = submitted.stream()
                    .map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(submittedCount), 1, RoundingMode.HALF_UP);

            long passed = submitted.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count();
            rate = BigDecimal.valueOf(passed * 100.0 / submittedCount).setScale(1, RoundingMode.HALF_UP);
        }

        List<Metric> list = new ArrayList<>();
        list.add(new Metric("学员总数", String.valueOf(students)));
        list.add(new Metric("题库数量", String.valueOf(banks)));
        list.add(new Metric("试题数量", String.valueOf(questions)));
        list.add(new Metric("试卷数量", String.valueOf(papers)));
        list.add(new Metric("考试计划", String.valueOf(plans)));
        list.add(new Metric("今日考试", String.valueOf(today)));
        list.add(new Metric("参考人次", String.valueOf(submittedCount)));
        list.add(new Metric("平均分", submittedCount == 0 ? "—" : avg.toPlainString()));
        list.add(new Metric("及格率", submittedCount == 0 ? "—" : rate.toPlainString() + "%"));
        list.add(new Metric("答卷总数", String.valueOf(attempts)));
        list.add(new Metric("待批阅", String.valueOf(pendingMarking())));
        list.add(new Metric("练习次数", "—"));
        return list;
    }

    /** 含主观题且未评完的答卷数 */
    public long pendingMarking() {
        return attemptMapper.selectCount(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getStatus, "SUBMITTED"));
    }
}
