package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 存量账号默认密码迁移：仍在使用旧默认密码的账号，统一升级为新默认密码。
 * <p>
 * 幂等且不越权：只有当密码仍然匹配旧默认值时才改写，用户自行修改过的密码不受影响。
 * 必须排在所有种子初始化器之后（@Order(100)），保证新播种的账号也能被覆盖到。
 */
@Slf4j
@Order(100)
@Component
@RequiredArgsConstructor
public class DefaultPasswordUpgrade implements ApplicationRunner {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery());
        int migrated = 0;
        for (SysUser user : users) {
            String hash = user.getPasswordHash();
            if (hash == null || !passwordEncoder.matches(DefaultPassword.LEGACY, hash)) {
                continue;
            }
            SysUser patch = new SysUser();
            patch.setId(user.getId());
            patch.setPasswordHash(passwordEncoder.encode(DefaultPassword.CURRENT));
            patch.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(patch);
            migrated++;
        }
        if (migrated > 0) {
            log.info("migrate default password: {} account(s) upgraded", migrated);
        }
    }
}
