package com.exam.system.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.common.R;
import com.exam.exam.entity.ExamAttempt;
import com.exam.exam.mapper.ExamAttemptMapper;
import com.exam.security.TokenStore;
import com.exam.system.entity.SysStudent;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysStudentMapper;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 个人中心：资料 + 成绩汇总 + 修改密码 */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

    private final SysUserMapper userMapper;
    private final SysStudentMapper studentMapper;
    private final ExamAttemptMapper attemptMapper;
    private final PasswordEncoder passwordEncoder;

    public record PasswordReq(String oldPassword, String newPassword) {
    }

    @GetMapping
    public R<Map<String, Object>> profile(Authentication auth) {
        TokenStore.Session s = (TokenStore.Session) auth.getDetails();
        SysUser user = userMapper.selectById(s.userId());
        SysStudent student = studentMapper.selectOne(Wrappers.<SysStudent>lambdaQuery()
                .eq(SysStudent::getUserId, s.userId()));

        List<ExamAttempt> submitted = attemptMapper.selectList(Wrappers.<ExamAttempt>lambdaQuery()
                .eq(ExamAttempt::getUserId, s.userId())
                .eq(ExamAttempt::getStatus, "SUBMITTED"));

        BigDecimal avg = BigDecimal.ZERO;
        BigDecimal best = BigDecimal.ZERO;
        long passed = submitted.stream().filter(a -> Boolean.TRUE.equals(a.getPassed())).count();
        if (!submitted.isEmpty()) {
            BigDecimal sum = submitted.stream()
                    .map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avg = sum.divide(BigDecimal.valueOf(submitted.size()), 1, RoundingMode.HALF_UP);
            best = submitted.stream()
                    .map(a -> a.getScore() == null ? BigDecimal.ZERO : a.getScore())
                    .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }
        double rate = submitted.isEmpty() ? 0 : Math.round(passed * 1000.0 / submitted.size()) / 10.0;

        Map<String, Object> stats = Map.of(
                "attemptCount", submitted.size(),
                "passedCount", passed,
                "avgScore", avg,
                "bestScore", best,
                "passRate", rate);

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("id", s.userId());
        body.put("username", s.username());
        body.put("name", user == null ? s.name() : user.getName());
        body.put("phone", user == null || user.getPhone() == null ? "" : user.getPhone());
        body.put("email", user == null || user.getEmail() == null ? "" : user.getEmail());
        body.put("roles", s.roles());
        body.put("lastLoginAt", user == null || user.getLastLoginAt() == null
                ? "" : user.getLastLoginAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        body.put("studentNo", student == null ? "" : student.getNo());
        body.put("orgName", student == null ? "" : student.getOrgName());
        body.put("level", student == null ? "" : student.getLevel());
        body.put("stats", stats);
        return R.ok(body);
    }

    @PostMapping("/password")
    public R<Void> changePassword(@RequestBody PasswordReq req, Authentication auth) {
        if (!StringUtils.hasText(req.newPassword()) || req.newPassword().length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        if (Objects.equals(req.oldPassword(), req.newPassword())) {
            throw new BizException("新密码不能与旧密码相同");
        }
        Long userId = (Long) auth.getPrincipal();
        SysUser u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(404, "用户不存在");
        }
        if (!passwordEncoder.matches(req.oldPassword() == null ? "" : req.oldPassword(), u.getPasswordHash())) {
            throw new BizException("原密码不正确");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userMapper.updateById(u);
        return R.ok();
    }
}
