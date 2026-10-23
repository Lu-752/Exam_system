package com.exam.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_student")
public class SysStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String no;
    private String name;
    /** 关联登录账号，可为空（仅档案） */
    private Long userId;
    private String gender;
    /** 归属组织 id（组织机构模块维护，orgName 为冗余展示字段） */
    private Long orgId;
    private String orgName;
    private String phone;
    private String level;
    private Integer examCount;
    /** ACTIVE / DISABLED */
    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
