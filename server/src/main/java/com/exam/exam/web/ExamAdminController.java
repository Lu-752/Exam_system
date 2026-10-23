package com.exam.exam.web;

import com.exam.common.R;
import com.exam.exam.dto.ExamDtos.PaperUpsert;
import com.exam.exam.dto.ExamDtos.PaperView;
import com.exam.exam.dto.ExamDtos.PlanUpsert;
import com.exam.exam.dto.ExamDtos.PlanView;
import com.exam.exam.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('paper:view','exam-plan:view')")
public class ExamAdminController {

    private final ExamService service;

    @GetMapping("/papers")
    public R<List<PaperView>> papers() {
        return R.ok(service.listPapers());
    }

    @PostMapping("/papers")
    @PreAuthorize("hasAnyAuthority('paper:view','question:view')")
    public R<PaperView> createPaper(@RequestBody PaperUpsert req) {
        return R.ok(service.createPaper(req));
    }

    @PostMapping("/papers/auto")
    @PreAuthorize("hasAnyAuthority('paper:view','question:view')")
    public R<PaperView> autoPaper(@RequestBody com.exam.exam.dto.ExamDtos.AutoPaperReq req) {
        return R.ok(service.autoCreatePaper(req));
    }

    @GetMapping("/exam-plans")
    public R<List<PlanView>> plans() {
        return R.ok(service.listPlans());
    }

    @PostMapping("/exam-plans")
    public R<PlanView> createPlan(@RequestBody PlanUpsert req) {
        return R.ok(service.createPlan(req));
    }

    /** 发布成绩：阅卷完成后学员才可查分看解析 */
    @PostMapping("/exam-plans/{id}/publish-score")
    @PreAuthorize("hasAuthority('score:publish')")
    public R<Void> publishScore(@PathVariable Long id) {
        service.publishScores(id, true);
        return R.ok();
    }

    /** 撤回成绩：误发布时回退，学员重新变为待发布 */
    @PostMapping("/exam-plans/{id}/unpublish-score")
    @PreAuthorize("hasAuthority('score:publish')")
    public R<Void> unpublishScore(@PathVariable Long id) {
        service.publishScores(id, false);
        return R.ok();
    }
}
