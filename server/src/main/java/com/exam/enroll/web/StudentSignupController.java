package com.exam.enroll.web;

import com.exam.common.R;
import com.exam.enroll.service.SignupService;
import com.exam.enroll.service.SignupService.AvailablePlan;
import com.exam.enroll.service.SignupService.SignupRow;
import com.exam.security.TokenStore;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 报名中心：学员端 */
@RestController
@RequestMapping("/api/v1/student/signups")
@RequiredArgsConstructor
public class StudentSignupController {

    private final SignupService service;

    public record SignupReq(Long planId) {
    }

    @GetMapping("/available")
    public R<List<AvailablePlan>> available(Authentication auth) {
        return R.ok(service.available(userId(auth)));
    }

    @GetMapping
    public R<List<SignupRow>> mine(Authentication auth) {
        return R.ok(service.mySignups(userId(auth)));
    }

    @PostMapping
    public R<SignupRow> signup(@RequestBody SignupReq req, Authentication auth) {
        TokenStore.Session s = (TokenStore.Session) auth.getDetails();
        return R.ok(service.signup(req.planId(), s.userId(), s.name()));
    }

    @DeleteMapping("/{id}")
    public R<Void> cancel(@PathVariable Long id, Authentication auth) {
        service.cancel(id, userId(auth));
        return R.ok();
    }

    private Long userId(Authentication auth) {
        return (Long) auth.getPrincipal();
    }
}
