package com.exam.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/** 培训 / 考试大纲 */
@Data
@TableName("sys_outline")
public class Outline {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    /** 分类：消防 / 电工 / 危化品 … */
    private String category;
    private String version;
    /** 大纲正文（纯文本，按行展示） */
    private String content;
    /** ACTIVE / DISABLED */
    private String status;
    private Integer sortNo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
