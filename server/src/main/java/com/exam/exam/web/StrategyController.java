package com.exam.exam.web;

import com.exam.common.R;
import com.exam.exam.service.StrategyService;
import com.exam.exam.service.StrategyService.GenerateResult;
import com.exam.exam.service.StrategyService.SaveReq;
import com.exam.exam.service.StrategyService.StrategyRow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/strategies")
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyService service;

    public record GenerateReq(String paperName) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('strategy:view')")
    public R<List<StrategyRow>> list() {
        return R.ok(service.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('strategy:view')")
    public R<StrategyRow> create(@RequestBody SaveReq req) {
        return R.ok(service.save(null, req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('strategy:view')")
    public R<StrategyRow> update(@PathVariable Long id, @RequestBody SaveReq req) {
        return R.ok(service.save(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('strategy:view')")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/generate")
    @PreAuthorize("hasAuthority('strategy:view')")
    public R<GenerateResult> generate(@PathVariable Long id, @RequestBody(required = false) GenerateReq req) {
        return R.ok(service.generate(id, req == null ? null : req.paperName()));
    }
}
