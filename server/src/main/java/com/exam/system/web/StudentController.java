package com.exam.system.web;

import com.exam.common.ImportReq;
import com.exam.common.ImportResult;
import com.exam.common.PageResult;
import com.exam.common.R;
import com.exam.system.entity.SysStudent;
import com.exam.system.service.StudentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/students")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('student:view')")
public class StudentController {

    private final StudentService service;

    @GetMapping
    public R<PageResult<SysStudent>> page(@RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long orgId,
                                          @RequestParam(required = false) String org,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size) {
        return R.ok(service.page(keyword, orgId, org, status, page, size));
    }

    @PostMapping("/import")
    public R<ImportResult> importCsv(@Valid @RequestBody ImportReq req) {
        return R.ok(service.importCsv(req.content(), Boolean.TRUE.equals(req.autoCreateOrg())));
    }

    @GetMapping("/import-template")
    public R<String> template() {
        return R.ok(StudentService.TEMPLATE);
    }

    @PostMapping
    public R<SysStudent> create(@RequestBody SysStudent req) {
        return R.ok(service.create(req));
    }

    @PutMapping("/{id}")
    public R<SysStudent> update(@PathVariable Long id, @RequestBody SysStudent req) {
        return R.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }
}
