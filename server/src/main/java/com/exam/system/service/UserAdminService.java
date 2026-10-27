package com.exam.system.service;

import org.springframework.util.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.security.TokenStore;
import com.exam.system.dto.AdminDtos.UserCreateReq;
import com.exam.system.dto.AdminDtos.UserRow;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 用户管理：列表 / 新建 / 启停 / 重置密码 / 分配角色 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final String DEFAULT_PASSWORD = com.exam.system.DefaultPassword.CURRENT;

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final TokenStore tokenStore;

    public List<UserRow> list(String keyword) {
        var q = Wrappers.<SysUser>lambdaQuery().orderByAsc(SysUser::getId);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getName, keyword));
        }
        List<SysUser> users = userMapper.selectList(q);

        // 一次查出用户-角色关系，避免 N+1
        Map<Long, List<Long>> roleIds = new HashMap<>();
        Map<Long, List<String>> roleNames = new HashMap<>();
        jdbc.query("SELECT ur.user_id, r.id, r.name FROM sys_user_role ur JOIN sys_role r ON r.id = ur.role_id",
                rs -> {
                    Long uid = rs.getLong(1);
                    roleIds.computeIfAbsent(uid, k -> new ArrayList<>()).add(rs.getLong(2));
                    roleNames.computeIfAbsent(uid, k -> new ArrayList<>()).add(rs.getString(3));
                });

        List<UserRow> rows = new ArrayList<>();
        for (SysUser u : users) {
            rows.add(new UserRow(u.getId(), u.getUsername(), u.getName(), u.getPhone(), u.getEmail(),
                    u.getStatus(), roleIds.getOrDefault(u.getId(), List.of()),
                    roleNames.getOrDefault(u.getId(), List.of()),
                    u.getLastLoginAt(), u.getCreatedAt()));
        }
        return rows;
    }

    @Transactional
    public UserRow create(UserCreateReq req) {
        Long exists = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username = ?",
                Long.class, req.username().trim());
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }

        SysUser u = new SysUser();
        u.setUsername(req.username().trim());
        u.setName(req.name().trim());
        u.setPasswordHash(passwordEncoder.encode(
                !StringUtils.hasText(req.password()) ? DEFAULT_PASSWORD : req.password()));
        u.setPhone(req.phone());
        u.setEmail(req.email());
        u.setStatus("ACTIVE");
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(u);

        bindRoles(u.getId(), req.roleIds());
        return list(null).stream().filter(r -> r.id().equals(u.getId())).findFirst()
                .orElseThrow(() -> new BizException(500, "创建后读取失败"));
    }

    @Transactional
    public void updateStatus(Long id, String status, Long currentUserId) {
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException("状态取值非法");
        }
        if (id.equals(currentUserId) && "DISABLED".equals(status)) {
            throw new BizException("不能停用自己的账号");
        }
        SysUser u = requireUser(id);
        u.setStatus(status);
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);

        if ("DISABLED".equals(status)) {
            tokenStore.removeByUser(id);   // 停用即踢下线
        }
    }

    @Transactional
    public void resetPassword(Long id) {
        SysUser u = requireUser(id);
        u.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        u.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(u);
        tokenStore.removeByUser(id);
    }

    @Transactional
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        bindRoles(id, roleIds);
    }

    private void bindRoles(Long userId, List<Long> roleIds) {
        jdbc.update("DELETE FROM sys_user_role WHERE user_id = ?", userId);
        if (roleIds == null) return;
        for (Long rid : roleIds) {
            if (rid == null) continue;
            jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, rid);
        }
    }

    private SysUser requireUser(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        return u;
    }
}
