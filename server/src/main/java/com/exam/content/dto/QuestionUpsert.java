package com.exam.content.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 试题新增请求。type=COMPOSITE 时通过 children 提交子题（子题不支持再次嵌套题冒题）。
 */
public record QuestionUpsert(Long bankId, String type, String stem, List<Option> options,
                             String answer, String analysis, BigDecimal score, String difficulty,
                             List<QuestionUpsert> children) {
}
