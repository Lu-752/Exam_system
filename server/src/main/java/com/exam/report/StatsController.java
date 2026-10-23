package com.exam.report;

import com.exam.common.R;
import com.exam.report.AnalysisService.AnalysisView;
import com.exam.report.StatisticsService.PlanDetail;
import com.exam.report.StatisticsService.PlanStat;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatisticsService statisticsService;
    private final AnalysisService analysisService;

    @GetMapping("/plans")
    public R<List<PlanStat>> plans() {
        return R.ok(statisticsService.planStats());
    }

    @GetMapping("/plans/{planId}")
    public R<PlanDetail> detail(@PathVariable Long planId) {
        return R.ok(statisticsService.detail(planId));
    }

    /** 按题目 / 题型维度的正确率分析 */
    @GetMapping("/plans/{planId}/questions")
    public R<AnalysisView> analysis(@PathVariable Long planId) {
        return R.ok(analysisService.planAnalysis(planId));
    }
}
