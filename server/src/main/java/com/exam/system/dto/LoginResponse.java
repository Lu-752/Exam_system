package com.exam.system.dto;

import java.util.List;

public record LoginResponse(String token, UserInfo user) {

    public record UserInfo(Long id, String username, String name, String avatar,
                           List<String> roles, List<String> perms) {
    }
}
