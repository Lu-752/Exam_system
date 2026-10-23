package com.exam.exam.web;

import com.exam.common.R;
import com.exam.exam.dto.MarkingDtos.*;
import com.exam.exam.service.MarkingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class MarkingController {

    private final MarkingService service;

    // ---------- 阅卷 ----------

    @GetMapping("/marking/pending")
    @PreAuthorize("hasAuthority('marking:view')")
    public R<List<PendingItem>> pending() {
        return R.ok(service.pending());
    }

    @GetMapping("/marking/attempts/{attemptId}")
    @PreAuthorize("hasAuthority('marking:view')")
    public R<AttemptReview> review(@PathVariable Long attemptId) {
        return R.ok(service.review(attemptId));
    }

    @PostMapping("/marking/answers/{answerId}")
    @PreAuthorize("hasAuthority('marking:view')")
    public R<AttemptReview> save(@PathVariable Long answerId, @RequestBody SaveScoreReq req) {
        return R.ok(service.save(answerId, req.score(), req.comment()));
    }

    // ---------- 监考 ----------

    @GetMapping("/proctor/{planId}")
    @PreAuthorize("hasAuthority('proctor:view')")
    public R<List<ProctorRow>> rows(@PathVariable Long planId) {
        return R.ok(service.proctorRows(planId));
    }

    @PostMapping("/proctor/attempts/{attemptId}/force")
    @PreAuthorize("hasAuthority('proctor:force')")
    public R<ProctorRow> force(@PathVariable Long attemptId) {
        return R.ok(service.forceSubmit(attemptId));
    }
}
