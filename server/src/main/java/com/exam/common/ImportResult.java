package com.exam.common;

import java.util.List;

/** 批量导入结果：逐行错误明细，前端可直接展示 */
public record ImportResult(int total, int success, int failed, List<RowError> errors) {

    public record RowError(int line, String message) {
    }

    public static ImportResult of(int total, int success, List<RowError> errors) {
        return new ImportResult(total, success, errors == null ? 0 : errors.size(),
                errors == null ? List.of() : errors);
    }
}
