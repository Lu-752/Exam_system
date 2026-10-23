package com.exam.common;

import jakarta.validation.constraints.NotBlank;

/** 文本导入请求：前端读取 CSV 文本后直接提交，避免 multipart 兼容问题 */
public record ImportReq(@NotBlank(message = "导入内容不能为空") String content,
                        Boolean autoCreateOrg,
                        Long bankId) {
}
