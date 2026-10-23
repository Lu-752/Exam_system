package com.exam.report;

import com.exam.common.R;
import com.exam.report.OverviewService.Metric;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final OverviewService overviewService;

    @GetMapping("/overview")
    public R<Map<String, Object>> overview() {
        List<Metric> items = overviewService.metrics();
        return R.ok(Map.of("items", items));
    }
}
