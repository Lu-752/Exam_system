package com.exam.content.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuestionView(Long id, Long bankId, Long parentId, String type, String stem,
                           List<Option> options, String answer, String analysis, BigDecimal score,
                           List<QuestionView> children) {
}
