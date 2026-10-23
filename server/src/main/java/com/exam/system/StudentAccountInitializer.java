package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.SysStudent;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysStudentMapper;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 演示学员账号（幂等）：student / 123456，只挂「学员中心」相关菜单。
 * 绑定一条 sys_student 记录，考试答卷上的考生姓名即该学员姓名。
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class StudentAccountInitializer implements ApplicationRunner {

    private static final String USERNAME = "student";
    private static final String RAW_PASSWORD = "123456";
    /** 学员可见的菜单路径 */
    private static final List<String> STUDENT_PATHS = List.of(
            "/student/exam-center", "/student/signup", "/student/practice", "/student/profile");

    private final SysUserMapper userMapper;
    private final SysStudentMapper studentMapper;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long roleId = ensureRole();

        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, USERNAME));

        SysStudent student = studentMapper.selectOne(Wrappers.<SysStudent>lambdaQuery()
                .isNull(SysStudent::getUserId)
                .eq(SysStudent::getStatus, "ACTIVE")
                .orderByAsc(SysStudent::getId)
                .last("LIMIT 1"));

        if (user == null) {
            String name = student != null ? student.getName() : "演示学员";
            user = new SysUser();
            user.setUsername(USERNAME);
            user.setPasswordHash(passwordEncoder.encode(RAW_PASSWORD));
            user.setName(name);
            user.setStatus("ACTIVE");
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("seed student account: {}/{} ({})", USERNAME, RAW_PASSWORD, name);
        }

        bindRole(user.getId(), roleId);
        bindMenus(roleId);
        bindStudent(user, student);
    }

    private Long ensureRole() {
        Long id = queryLong("SELECT id FROM sys_role WHERE code = 'STUDENT'");
        if (id == null) {
            jdbc.update("INSERT INTO sys_role (code, name, remark, created_at) VALUES (?, ?, ?, ?)",
                    "STUDENT", "学员", "仅可参加考试、查看本人成绩", LocalDateTime.now());
            id = queryLong("SELECT id FROM sys_role WHERE code = 'STUDENT'");
            log.info("seed role: STUDENT id={}", id);
        }
        return id;
    }

    private void bindRole(Long userId, Long roleId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ? AND role_id = ?", Long.class, userId, roleId);
        if (n != null && n == 0) {
            jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, ?)", userId, roleId);
        }
    }

    /** 学员菜单：目录 + 学员中心下 4 个页面（不含首页概览等管理页面） */
    private void bindMenus(Long roleId) {
        for (String path : STUDENT_PATHS) {
            List<Long> ids = jdbc.queryForList(
                    "SELECT id FROM sys_menu WHERE path = ?", Long.class, path);
            for (Long menuId : ids) {
                grant(roleId, menuId);
                Long parent = queryLong("SELECT parent_id FROM sys_menu WHERE id = " + menuId);
                if (parent != null) {
                    grant(roleId, parent);
                }
            }
        }
    }

    private void grant(Long roleId, Long menuId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_role_menu WHERE role_id = ? AND menu_id = ?", Long.class, roleId, menuId);
        if (n != null && n == 0) {
            jdbc.update("INSERT INTO sys_role_menu (role_id, menu_id) VALUES (?, ?)", roleId, menuId);
        }
    }

    /** 学员账号与该学员档案一一绑定 */
    private void bindStudent(SysUser user, SysStudent candidate) {
        SysStudent bound = studentMapper.selectOne(Wrappers.<SysStudent>lambdaQuery()
                .eq(SysStudent::getUserId, user.getId()));
        if (bound != null) {
            if (!bound.getName().equals(user.getName())) {
                SysUser patch = new SysUser();
                patch.setId(user.getId());
                patch.setName(bound.getName());
                userMapper.updateById(patch);
            }
            return;
        }
        if (candidate == null) {
            log.warn("no unbound student record available for account {}", USERNAME);
            return;
        }
        candidate.setUserId(user.getId());
        candidate.setUpdatedAt(LocalDateTime.now());
        studentMapper.updateById(candidate);

        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setName(candidate.getName());
        userMapper.updateById(patch);
        log.info("bind student account -> {} ({})", candidate.getName(), candidate.getNo());
    }

    private Long queryLong(String sql) {
        List<Long> list = jdbc.queryForList(sql, Long.class);
        return list.isEmpty() ? null : list.get(0);
    }
}
