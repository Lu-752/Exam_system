package com.exam.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.system.dto.AdminDtos.RoleRow;
import com.exam.system.dto.AdminDtos.RoleUpsert;
import com.exam.system.dto.MenuNode;
import com.exam.system.entity.SysMenu;
import com.exam.system.entity.SysRole;
import com.exam.system.mapper.SysMenuMapper;
import com.exam.system.mapper.SysRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** 角色管理：列表 / 菜单权限树 / 授权保存 */
@Service
@RequiredArgsConstructor
public class RoleAdminService {

    private final SysRoleMapper roleMapper;
    private final SysMenuMapper menuMapper;
    private final JdbcTemplate jdbc;

    public List<RoleRow> list() {
        List<SysRole> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery().orderByAsc(SysRole::getId));
        List<RoleRow> rows = new ArrayList<>();
        for (SysRole r : roles) {
            Integer users = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_user_role WHERE role_id = ?", Integer.class, r.getId());
            Integer menus = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ?", Integer.class, r.getId());
            rows.add(new RoleRow(r.getId(), r.getCode(), r.getName(), r.getRemark(),
                    users == null ? 0 : users, menus == null ? 0 : menus));
        }
        return rows;
    }

    @Transactional
    public RoleRow create(RoleUpsert req) {
        String code = req.code().trim().toUpperCase();
        String name = req.name().trim();
        Long dupCode = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, code));
        if (dupCode != null && dupCode > 0) {
            throw new BizException("角色编码已存在：" + code);
        }
        Long dupName = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getName, name));
        if (dupName != null && dupName > 0) {
            throw new BizException("角色名称已存在：" + name);
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(name);
        role.setRemark(req.remark());
        role.setCreatedAt(LocalDateTime.now());
        roleMapper.insert(role);

        // 可选：以现有角色为模板复制权限
        if (req.menuIds() != null) {
            saveMenus(role.getId(), req.menuIds());
        }
        return row(role);
    }

    @Transactional
    public RoleRow update(Long id, RoleUpsert req) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        boolean builtin = "SUPER_ADMIN".equals(role.getCode());
        String code = req.code().trim().toUpperCase();
        String name = req.name().trim();

        if (!code.equals(role.getCode())) {
            if (builtin) {
                throw new BizException("内置角色的编码不可修改");
            }
            Long dup = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getCode, code).ne(SysRole::getId, id));
            if (dup != null && dup > 0) {
                throw new BizException("角色编码已存在：" + code);
            }
            role.setCode(code);
        }
        if (!name.equals(role.getName())) {
            Long dup = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                    .eq(SysRole::getName, name).ne(SysRole::getId, id));
            if (dup != null && dup > 0) {
                throw new BizException("角色名称已存在：" + name);
            }
            role.setName(name);
        }
        role.setRemark(req.remark());
        roleMapper.updateById(role);
        return row(role);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BizException("内置超级管理员角色不可删除");
        }
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE role_id = ?", Integer.class, id);
        if (users != null && users > 0) {
            throw new BizException("该角色下还有 " + users + " 个用户，请先调整用户角色");
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", id);
        roleMapper.deleteById(id);
    }

    private RoleRow row(SysRole r) {
        Integer users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE role_id = ?", Integer.class, r.getId());
        Integer menus = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ?", Integer.class, r.getId());
        return new RoleRow(r.getId(), r.getCode(), r.getName(), r.getRemark(),
                users == null ? 0 : users, menus == null ? 0 : menus);
    }

    /** 全量菜单树（用于授权勾选） */
    public List<MenuNode> menuTree() {
        List<SysMenu> flat = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                .orderByAsc(SysMenu::getSortNo).orderByAsc(SysMenu::getId));

        Map<Long, MenuNode> index = new LinkedHashMap<>();
        for (SysMenu m : flat) {
            MenuNode n = new MenuNode();
            n.setId(m.getId());
            n.setParentId(m.getParentId());
            n.setName(m.getName());
            n.setPath(m.getPath());
            n.setIcon(m.getIcon());
            n.setPerm(m.getPerm());
            n.setType(m.getType());
            n.setSortNo(m.getSortNo());
            index.put(m.getId(), n);
        }

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode n : index.values()) {
            MenuNode parent = n.getParentId() == null ? null : index.get(n.getParentId());
            if (parent == null) roots.add(n);
            else parent.getChildren().add(n);
        }
        return roots;
    }

    public List<Long> menuIds(Long roleId) {
        return jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id = ?", Long.class, roleId);
    }

    @Transactional
    public void saveMenus(Long roleId, List<Long> menuIds) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        if ("SUPER_ADMIN".equals(role.getCode())) {
            throw new BizException("超级管理员拥有全部权限，不支持收窄");
        }
        jdbc.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        if (menuIds == null) return;
        for (Long mid : new LinkedHashSet<>(menuIds)) {
            if (mid == null) continue;
            jdbc.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, mid);
        }
    }
}
