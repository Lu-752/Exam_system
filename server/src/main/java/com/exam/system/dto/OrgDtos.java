package com.exam.system.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public final class OrgDtos {

    private OrgDtos() {
    }

    /** 组织树节点：memberCount = 直属人数，totalMemberCount = 含下级总人数 */
    public record OrgNode(Long id, Long parentId, String name, String code, String leader, String phone,
                          String status, String remark, Integer sortNo,
                          int memberCount, int totalMemberCount, List<OrgNode> children) {
    }

    public record OrgUpsert(@NotBlank(message = "组织名称不能为空") String name,
                            String code, String leader, String phone, String status,
                            String remark, Integer sortNo, Long parentId) {
    }

    public record OrgMember(Long id, String no, String name, String gender, String phone,
                            String level, Integer examCount, String status, String orgName) {
    }

    public record OrgStats(int orgCount, int memberCount, int unassigned) {
    }
}
