package com.exam.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record UserRow(Long id, String username, String name, String phone, String email,
                          String status, List<Long> roleIds, List<String> roleNames,
                          LocalDateTime lastLoginAt, LocalDateTime createdAt) {
    }

    public record UserCreateReq(@NotBlank(message = "用户名不能为空") String username,
                                @NotBlank(message = "姓名不能为空") String name,
                                @Size(min = 6, max = 32, message = "密码长度需 6-32 位") String password,
                                String phone, String email, List<Long> roleIds) {
    }

    public record StatusReq(@NotBlank String status) {
    }

    public record RoleIdsReq(List<Long> roleIds) {
    }

    public record RoleRow(Long id, String code, String name, String remark, int userCount, int menuCount) {
    }

    public record RoleUpsert(@NotBlank(message = "角色编码不能为空") String code,
                             @NotBlank(message = "角色名称不能为空") String name,
                             String remark, List<Long> menuIds) {
    }

    public record MenuIdsReq(List<Long> menuIds) {
    }
}
