package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.SysMenu;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysMenuMapper;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 初始化：超级管理员 + 角色 + 菜单权限树。
 * 幂等：已有用户则直接跳过，避免重复启动重复插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 守卫只看 admin 本身：学员账号初始化器（@Order）可能先插入过数据
        Long adminCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, "admin"));
        if (adminCount != null && adminCount > 0) {
            log.info("skip seed: admin already exists");
            return;
        }

        List<Long> menuIds = seedMenus();

        jdbc.update("INSERT INTO sys_role (code, name, remark, created_at) VALUES (?, ?, ?, ?)",
                "SUPER_ADMIN", "超级管理员", "内置角色，拥有全部权限", LocalDateTime.now());
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE code = 'SUPER_ADMIN'", Long.class);

        for (Long menuId : menuIds) {
            jdbc.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
        }

        SysUser admin = new SysUser();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode(DefaultPassword.CURRENT));
        admin.setName("管理员");
        admin.setStatus("ACTIVE");
        admin.setCreatedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(admin);

        jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", admin.getId(), roleId);

        log.info("seed done: admin (default password), menus={}", menuIds.size());
    }

    private List<Long> seedMenus() {
        List<Long> ids = new ArrayList<>();

        ids.add(insertMenu(null, "首页", "/dashboard", "home", "dashboard:view", "MENU", 1));

        Long student = insertMenu(null, "学员中心", null, "users", null, "DIR", 10);
        ids.add(student);
        ids.add(insertMenu(student, "考试中心", "/student/exam-center", null, "student:exam", "MENU", 11));
        ids.add(insertMenu(student, "报名中心", "/student/signup", null, "student:signup", "MENU", 12));
        ids.add(insertMenu(student, "练习中心", "/student/practice", null, "student:practice", "MENU", 13));
        ids.add(insertMenu(student, "个人中心", "/student/profile", null, "student:profile", "MENU", 14));

        Long exam = insertMenu(null, "考试中心", null, "doc", null, "DIR", 20);
        ids.add(exam);
        ids.add(insertMenu(exam, "大纲管理", "/admin/outlines", null, "outline:view", "MENU", 21));
        ids.add(insertMenu(exam, "题库管理", "/admin/question-banks", null, "bank:view", "MENU", 22));
        ids.add(insertMenu(exam, "试题管理", "/admin/questions", null, "question:view", "MENU", 23));
        ids.add(insertMenu(exam, "组卷策略", "/admin/strategies", null, "strategy:view", "MENU", 24));
        ids.add(insertMenu(exam, "试卷管理", "/admin/papers", null, "paper:view", "MENU", 25));
        ids.add(insertMenu(exam, "考试计划", "/admin/exam-plans", null, "exam-plan:view", "MENU", 26));
        ids.add(insertMenu(exam, "考试报名", "/admin/signups", null, "signup:view", "MENU", 27));
        ids.add(insertMenu(exam, "考试监控", "/admin/monitor", null, "proctor:view", "MENU", 28));
        ids.add(insertMenu(exam, "交卷记录", "/admin/records", null, "record:view", "MENU", 29));
        ids.add(insertMenu(exam, "考试阅卷", "/admin/marking", null, "marking:view", "MENU", 30));

        // 按钮级权限：强制交卷 + 发布成绩（考试计划发布成绩，M7 闸门，与学习中心无关，必须保留）
        ids.add(insertMenu(exam, "强制交卷", null, null, "proctor:force", "BUTTON", 31));
        ids.add(insertMenu(exam, "发布成绩", null, null, "score:publish", "BUTTON", 32));

        Long system = insertMenu(null, "系统管理", null, "setting", null, "DIR", 50);
        ids.add(system);
        ids.add(insertMenu(system, "用户管理", "/system/users", null, "user:view", "MENU", 51));
        ids.add(insertMenu(system, "角色管理", "/system/roles", null, "role:view", "MENU", 52));
        ids.add(insertMenu(system, "组织机构", "/system/orgs", null, "org:view", "MENU", 53));
        ids.add(insertMenu(system, "字典管理", "/system/dicts", null, "dict:view", "MENU", 54));
        ids.add(insertMenu(system, "系统参数", "/system/params", null, "param:view", "MENU", 55));

        return ids;
    }

    private Long insertMenu(Long parentId, String name, String path, String icon,
                            String perm, String type, int sortNo) {
        SysMenu m = new SysMenu();
        m.setParentId(parentId);
        m.setName(name);
        m.setPath(path);
        m.setIcon(icon);
        m.setPerm(perm);
        m.setType(type);
        m.setSortNo(sortNo);
        menuMapper.insert(m);
        return m.getId();
    }
}
