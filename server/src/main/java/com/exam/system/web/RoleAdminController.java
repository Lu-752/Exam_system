package com.exam.system.web;

import com.exam.common.R;
import com.exam.system.dto.AdminDtos.MenuIdsReq;
import com.exam.system.dto.AdminDtos.RoleRow;
import com.exam.system.dto.AdminDtos.RoleUpsert;
import com.exam.system.dto.MenuNode;
import com.exam.system.service.RoleAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
public class RoleAdminController {

    private final RoleAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('role:view')")
    public R<List<RoleRow>> list() {
        return R.ok(service.list());
    }

    @GetMapping("/menu-tree")
    @PreAuthorize("hasAuthority('role:view')")
    public R<List<MenuNode>> menuTree() {
        return R.ok(service.menuTree());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('role:view')")
    public R<RoleRow> create(@Valid @RequestBody RoleUpsert req) {
        return R.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('role:view')")
    public R<RoleRow> update(@PathVariable Long id, @Valid @RequestBody RoleUpsert req) {
        return R.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('role:view')")
    public R<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return R.ok();
    }

    @GetMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('role:view')")
    public R<List<Long>> menuIds(@PathVariable Long id) {
        return R.ok(service.menuIds(id));
    }

    @PutMapping("/{id}/menus")
    @PreAuthorize("hasAuthority('role:view')")
    public R<Void> saveMenus(@PathVariable Long id, @RequestBody MenuIdsReq req) {
        service.saveMenus(id, req.menuIds());
        return R.ok();
    }
}
