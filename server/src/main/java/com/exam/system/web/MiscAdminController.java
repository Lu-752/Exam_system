package com.exam.system.web;

import com.exam.common.R;
import com.exam.system.dto.MiscDtos.*;
import com.exam.system.entity.DictItem;
import com.exam.system.entity.Outline;
import com.exam.system.entity.SysConfig;
import com.exam.system.service.MiscAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 大纲 / 字典 / 系统参数 管理端接口 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class MiscAdminController {

    private final MiscAdminService service;

    // ---------- 大纲 ----------

    @GetMapping("/outlines")
    @PreAuthorize("hasAuthority('outline:view')")
    public R<List<MiscAdminService.OutlineRow>> outlines(@RequestParam(required = false) String keyword,
                                                         @RequestParam(required = false) String status) {
        return R.ok(service.outlines(keyword, status));
    }

    @PostMapping("/outlines")
    @PreAuthorize("hasAuthority('outline:view')")
    public R<Outline> createOutline(@Valid @RequestBody OutlineUpsert req) {
        return R.ok(service.createOutline(req));
    }

    @PutMapping("/outlines/{id}")
    @PreAuthorize("hasAuthority('outline:view')")
    public R<Outline> updateOutline(@PathVariable Long id, @Valid @RequestBody OutlineUpsert req) {
        return R.ok(service.updateOutline(id, req));
    }

    @DeleteMapping("/outlines/{id}")
    @PreAuthorize("hasAuthority('outline:view')")
    public R<Void> deleteOutline(@PathVariable Long id) {
        service.deleteOutline(id);
        return R.ok();
    }

    // ---------- 字典 ----------

    @GetMapping("/dicts/types")
    @PreAuthorize("hasAuthority('dict:view')")
    public R<List<String>> dictTypes() {
        return R.ok(service.dictTypes());
    }

    @GetMapping("/dicts")
    @PreAuthorize("hasAuthority('dict:view')")
    public R<List<DictItem>> dictItems(@RequestParam(required = false) String dictType,
                                       @RequestParam(required = false) String keyword) {
        return R.ok(service.dictItems(dictType, keyword));
    }

    @PostMapping("/dicts")
    @PreAuthorize("hasAuthority('dict:view')")
    public R<DictItem> createDict(@Valid @RequestBody DictUpsert req) {
        return R.ok(service.createDict(req));
    }

    @PutMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('dict:view')")
    public R<DictItem> updateDict(@PathVariable Long id, @Valid @RequestBody DictUpsert req) {
        return R.ok(service.updateDict(id, req));
    }

    @DeleteMapping("/dicts/{id}")
    @PreAuthorize("hasAuthority('dict:view')")
    public R<Void> deleteDict(@PathVariable Long id) {
        service.deleteDict(id);
        return R.ok();
    }

    // ---------- 系统参数 ----------

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('param:view')")
    public R<List<SysConfig>> configs(@RequestParam(required = false) String keyword) {
        return R.ok(service.configs(keyword));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('param:view')")
    public R<SysConfig> createConfig(@Valid @RequestBody ConfigUpsert req) {
        return R.ok(service.createConfig(req));
    }

    @PutMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('param:view')")
    public R<SysConfig> updateConfig(@PathVariable Long id, @Valid @RequestBody ConfigUpsert req) {
        return R.ok(service.updateConfig(id, req));
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('param:view')")
    public R<Void> deleteConfig(@PathVariable Long id) {
        service.deleteConfig(id);
        return R.ok();
    }
}
