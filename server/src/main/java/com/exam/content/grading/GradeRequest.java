package com.exam.content.grading;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 判分请求/结果。设计为不可变，便于单测与并发调用。
 */
public record GradeRequest(
        QuestionType type,
        /** 正确答案：单选题 1 个、多选题多个、判断题 T/F、填空题按空顺序（空之间用 | 分隔，同一空的多个可接受答案用 / 分隔） */
        java.util.List<String> correct,
        /** 考生所选（单选/多选/判断） */
        java.util.List<String> selected,
        /** 考生作答文本（填空/问答） */
        String text,
        /** 本题满分 */
        double fullScore
) {
    public record GradeResult(double score, boolean correct, String remark) {

        static GradeResult of(double score, boolean correct, String remark) {
            double safe = Math.max(0, Math.min(score, Double.MAX_VALUE));
            return new GradeResult(round(safe), correct, remark);
        }

        static double round(double v) {
            return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
        }
    }
}
