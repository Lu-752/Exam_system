package com.exam.system.web;

import com.exam.common.R;
import com.exam.security.TokenStore;
import com.exam.system.dto.AdminDtos.*;
import com.exam.system.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService service;

    @GetMapping
    @PreAuthorize("hasAuthority('user:view')")
    public R<List<UserRow>> list(@RequestParam(required = false) String keyword) {
        return R.ok(service.list(keyword));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:view')")
    public R<UserRow> create(@Valid @RequestBody UserCreateReq req) {
        return R.ok(service.create(req));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:view')")
    public R<Void> status(@PathVariable Long id, @Valid @RequestBody StatusReq req, Authentication auth) {
        service.updateStatus(id, req.status(), (Long) auth.getPrincipal());
        return R.ok();
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:view')")
    public R<Void> resetPassword(@PathVariable Long id) {
        service.resetPassword(id);
        return R.ok();
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('user:view')")
    public R<Void> roles(@PathVariable Long id, @RequestBody RoleIdsReq req) {
        service.assignRoles(id, req.roleIds());
        return R.ok();
    }
}
