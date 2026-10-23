package com.exam.enroll.web;

import com.exam.common.R;
import com.exam.enroll.service.SignupService;
import com.exam.enroll.service.SignupService.SignupRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 报名审核：管理端 */
@RestController
@RequestMapping("/api/v1/admin/signups")
@RequiredArgsConstructor
class AdminSignupController {

    private final SignupService service;

    public record ReviewReq(String status, String remark) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('signup:view')")
    public R<List<SignupRow>> list(@RequestParam(required = false) Long planId,
                                   @RequestParam(required = false) String status) {
        return R.ok(service.adminList(planId, status));
    }

    @PutMapping("/{id}/review")
    @PreAuthorize("hasAuthority('signup:view')")
    public R<Void> review(@PathVariable Long id, @RequestBody ReviewReq req) {
        service.review(id, req.status(), req.remark());
        return R.ok();
    }
}
