package com.exam.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.security.TokenStore;
import com.exam.system.dto.LoginResponse;
import com.exam.system.dto.MenuNode;
import com.exam.system.entity.SysMenu;
import com.exam.system.entity.SysUser;
import com.exam.system.mapper.SysMenuMapper;
import com.exam.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public LoginResponse login(String username, String rawPassword) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BizException("用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BizException("账号已被停用，请联系管理员");
        }

        List<String> roles = userMapper.selectRoleCodes(user.getId());
        List<String> perms = userMapper.selectPerms(user.getId());

        // 单端在线：考试系统要求同一账号不并发登录，新登录踢掉旧会话
        tokenStore.removeByUser(user.getId());
        String token = tokenStore.create(user.getId(), user.getUsername(), user.getName(), roles, perms);

        SysUser patch = new SysUser();
        patch.setId(user.getId());
        patch.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(patch);

        return new LoginResponse(token, new LoginResponse.UserInfo(
                user.getId(), user.getUsername(), user.getName(),
                nameToAvatar(user.getName()), roles, perms));
    }

    public LoginResponse.UserInfo me(TokenStore.Session session) {
        return new LoginResponse.UserInfo(session.userId(), session.username(), session.name(),
                nameToAvatar(session.name()), session.roles(), session.perms());
    }

    public List<MenuNode> menus(Long userId) {
        List<SysMenu> flat = menuMapper.selectByUserId(userId);
        Map<Long, MenuNode> index = new LinkedHashMap<>();
        for (SysMenu m : flat) {
            MenuNode node = new MenuNode();
            node.setId(m.getId());
            node.setParentId(m.getParentId());
            node.setName(m.getName());
            node.setPath(m.getPath());
            node.setIcon(m.getIcon());
            node.setPerm(m.getPerm());
            node.setType(m.getType());
            node.setSortNo(m.getSortNo());
            index.put(m.getId(), node);
        }

        List<MenuNode> roots = new ArrayList<>();
        for (MenuNode node : index.values()) {
            Long pid = node.getParentId();
            MenuNode parent = pid == null ? null : index.get(pid);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    public void logout(String token) {
        tokenStore.remove(token);
    }

    private String nameToAvatar(String name) {
        return (name == null || name.isEmpty()) ? "U" : name.substring(0, 1);
    }
}
