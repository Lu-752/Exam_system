package com.exam.system.dto;

import jakarta.validation.constraints.NotBlank;

public final class MiscDtos {

    private MiscDtos() {
    }

    public record OutlineUpsert(@NotBlank String name, String category, String version,
                                String content, String status, Integer sortNo) {
    }

    public record DictUpsert(@NotBlank String dictType, @NotBlank String code, @NotBlank String label,
                             String value, Integer sortNo, String status, String remark) {
    }

    public record ConfigUpsert(@NotBlank String paramKey, String paramValue, String remark) {
    }
}
