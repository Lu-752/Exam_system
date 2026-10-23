package com.exam.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M0 阶段的令牌存储：进程内 Map。
 * 多节点部署前必须替换为 Redis（键 token -> session，TTL 8h），接口保持不变。
 */
@Component
public class TokenStore {

    public record Session(Long userId, String username, String name,
                          List<String> roles, List<String> perms, long expireAt) {
        boolean expired() {
            return System.currentTimeMillis() > expireAt;
        }
    }

    private static final long TTL_MS = 8 * 60 * 60 * 1000L;

    private final Map<String, Session> store = new ConcurrentHashMap<>();

    public String create(Long userId, String username, String name, List<String> roles, List<String> perms) {
        String token = UUID.randomUUID().toString().replace("-", "");
        store.put(token, new Session(userId, username, name, roles, perms, System.currentTimeMillis() + TTL_MS));
        return token;
    }

    public Session get(String token) {
        Session s = store.get(token);
        if (s == null) {
            return null;
        }
        if (s.expired()) {
            store.remove(token);
            return null;
        }
        return s;
    }

    public void remove(String token) {
        store.remove(token);
    }

    /** 同一账号在其他端登录时踢掉旧会话 */
    public void removeByUser(Long userId) {
        store.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
    }
}
