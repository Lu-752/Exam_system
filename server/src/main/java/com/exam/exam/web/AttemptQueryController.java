package com.exam.exam.web;

import com.exam.common.R;
import com.exam.exam.service.AttemptQueryService;
import com.exam.exam.service.AttemptQueryService.AttemptRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/attempts")
@RequiredArgsConstructor
public class AttemptQueryController {

    private final AttemptQueryService service;

    @GetMapping
    @PreAuthorize("hasAuthority('record:view')")
    public R<List<AttemptRow>> list(@RequestParam(required = false) Long planId,
                                    @RequestParam(required = false) String status,
                                    @RequestParam(required = false) String keyword) {
        return R.ok(service.list(planId, status, keyword));
    }
}
