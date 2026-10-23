package com.exam.system.web;

import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.system.dto.OrgDtos.OrgMember;
import com.exam.system.dto.OrgDtos.OrgNode;
import com.exam.system.dto.OrgDtos.OrgStats;
import com.exam.system.dto.OrgDtos.OrgUpsert;
import com.exam.system.entity.Org;
import com.exam.system.service.OrgService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/orgs")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService service;

    /** 组织树；学员/用户管理页面也要用到，放开给 student:view / user:view */
    @GetMapping
    @PreAuthorize("hasAuthority('org:view') or hasAuthority('student:view') or hasAuthority('user:view')")
    public R<List<OrgNode>> tree(@RequestParam(required = false) String keyword) {
        return R.ok(service.tree(keyword));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('org:view')")
    public R<OrgStats> stats() {
        return R.ok(service.stats());
    }

    @GetMapping("/{id}/members")
    @PreAuthorize("hasAuthority('org:view') or hasAuthority('student:view')")
    public R<PageResult<OrgMember>> members(@PathVariable Long id,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.members(id, keyword, page, size));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('org:view')")
    public R<Org> create(@Valid @RequestBody OrgUpsert req) {
        return R.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('org:view')")
    public R<Org> update(@PathVariable Long id, @Valid @RequestBody OrgUpsert req) {
        return R.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('org:view')")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
