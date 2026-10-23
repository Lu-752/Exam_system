package com.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_plan")
public class ExamPlan {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long paperId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startAt;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endAt;
    private Integer durationSec;
    private BigDecimal passScore;
    private Integer maxAttempts;
    private String config;
    private String status;
    /** 成绩是否已发布：false 时学员看不到分数与答卷解析 */
    private Boolean scorePublished;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
