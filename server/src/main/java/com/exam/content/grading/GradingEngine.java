package com.exam.content.grading;

import java.util.List;
import java.util.Locale;

/**
 * 判分引擎（客观题自动判分，主观题转人工）。
 *
 * <p>口径（对应《功能清单》Q1/Q2 的默认建议，均可在调用侧切换）：
 * <ul>
 *   <li>多选：默认全对才得分；开启多选部分分时，选中为正确集合真子集且非空得半分</li>
 *   <li>填空：多空按命中比例给分；同一空多个可接受答案用 "|" 分隔（或关系）</li>
 *   <li>归一化：去首尾空白、去全部空格、全角转半角、忽略大小写</li>
 * </ul>
 */
public final class GradingEngine {

    /** 多选少选是否给半分（产品口径 Q1） */
    private static final boolean MULTI_PARTIAL_CREDIT = false;

    private GradingEngine() {
    }

    public static GradeRequest.GradeResult grade(GradeRequest req) {
        if (req == null || req.type() == null) {
            return GradeRequest.GradeResult.of(0, false, "无判分信息");
        }
        if (req.fullScore() <= 0) {
            return GradeRequest.GradeResult.of(0, false, "分值配置错误");
        }

        return switch (req.type()) {
            case SC, TF -> gradeSingle(req);
            case MC -> gradeMultiple(req);
            case BLANK -> gradeBlank(req);
            case ESSAY -> GradeRequest.GradeResult.of(0, false, "主观题，待人工阅卷");
            case COMPOSITE -> GradeRequest.GradeResult.of(0, false, "复合题，按子题累加");
        };
    }

    private static GradeRequest.GradeResult gradeSingle(GradeRequest req) {
        String correct = first(req.correct());
        String picked = req.selected() == null || req.selected().isEmpty() ? "" : req.selected().get(0);
        if (correct.isEmpty() || picked.isEmpty()) {
            return GradeRequest.GradeResult.of(0, false, "未作答");
        }
        boolean ok = normalize(correct).equals(normalize(picked));
        return GradeRequest.GradeResult.of(ok ? req.fullScore() : 0, ok, ok ? "正确" : "错误");
    }

    private static GradeRequest.GradeResult gradeMultiple(GradeRequest req) {
        List<String> correct = (req.correct() == null ? List.<String>of() : req.correct())
                .stream().map(GradingEngine::normalize).toList();
        List<String> picked = (req.selected() == null ? List.<String>of() : req.selected())
                .stream().map(GradingEngine::normalize).distinct().toList();

        if (correct.isEmpty() || picked.isEmpty()) {
            return GradeRequest.GradeResult.of(0, false, "未作答");
        }
        if (picked.size() == correct.size() && picked.containsAll(correct)) {
            return GradeRequest.GradeResult.of(req.fullScore(), true, "正确");
        }
        boolean subset = correct.containsAll(picked);
        if (subset && MULTI_PARTIAL_CREDIT) {
            return GradeRequest.GradeResult.of(req.fullScore() * 0.5, false, "少选，得半分");
        }
        return GradeRequest.GradeResult.of(0, false, subset ? "少选" : "错选");
    }

    private static GradeRequest.GradeResult gradeBlank(GradeRequest req) {
        List<String> keys = req.correct() == null ? List.of() : req.correct();
        if (keys.isEmpty()) {
            return GradeRequest.GradeResult.of(0, false, "未配置参考答案");
        }

        String raw = req.text() == null ? "" : req.text().replace("\r", "").replace("\n", "|");
        String[] parts = raw.split("\\|", -1);

        int hit = 0;
        for (int i = 0; i < keys.size(); i++) {
            String submitted = i < parts.length ? parts[i] : "";
            if (matchesAny(submitted, keys.get(i))) {
                hit++;
            }
        }
        if (hit == 0) {
            return GradeRequest.GradeResult.of(0, false, "未命中");
        }
        double score = req.fullScore() * hit / keys.size();
        boolean full = hit == keys.size();
        return GradeRequest.GradeResult.of(score, full, full ? "正确" : ("命中 " + hit + "/" + keys.size() + " 空"));
    }

    private static boolean matchesAny(String submitted, String alternatives) {
        String s = normalize(submitted);
        if (s.isEmpty() || alternatives == null) {
            return false;
        }
        for (String alt : alternatives.split("/")) {
            if (s.equals(normalize(alt))) {
                return true;
            }
        }
        return false;
    }

    private static String first(List<String> list) {
        return (list == null || list.isEmpty() || list.get(0) == null) ? "" : list.get(0);
    }

    /** 去空白 + 全角转半角 + 小写 */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\u3000') {
                continue;
            }
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            }
            if (Character.isWhitespace(c)) {
                continue;
            }
            sb.append(c);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
