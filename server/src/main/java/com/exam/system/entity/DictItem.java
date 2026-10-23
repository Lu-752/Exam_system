package com.exam.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/** 数据字典项（按 dict_type 分组） */
@Data
@TableName("sys_dict_item")
public class DictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型标识，如 grade / difficulty */
    private String dictType;
    private String code;
    private String label;
    /** 列名 item_value：value 是 H2 保留字，JSON 输出仍叫 value */
    @JsonProperty("value")
    private String itemValue;
    private Integer sortNo;
    /** ACTIVE / DISABLED */
    private String status;
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
