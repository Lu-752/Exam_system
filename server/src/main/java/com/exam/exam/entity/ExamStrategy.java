package com.exam.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_strategy")
public class ExamStrategy {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long bankId;
    /** JSON：[{"type":"SC","count":5,"score":4}] */
    private String rules;
    private BigDecimal totalScore;
    private BigDecimal passScore;
    private Integer durationSec;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
