package com.exam.content.grading;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("判分引擎")
class GradingEngineTest {

    private static GradeRequest req(QuestionType t, List<String> correct, List<String> selected,
                                    String text, double full) {
        return new GradeRequest(t, correct, selected, text, full);
    }

    @Test
    @DisplayName("单选：选中正确项得满分，错选得 0")
    void singleChoice() {
        var ok = GradingEngine.grade(req(QuestionType.SC, List.of("B"), List.of("B"), null, 5));
        assertEquals(5, ok.score());
        assertTrue(ok.correct());

        var bad = GradingEngine.grade(req(QuestionType.SC, List.of("B"), List.of("C"), null, 5));
        assertEquals(0, bad.score());
        assertFalse(bad.correct());
    }

    @Test
    @DisplayName("单选：未作答得 0，不抛异常")
    void singleChoiceEmpty() {
        var r = GradingEngine.grade(req(QuestionType.SC, List.of("A"), List.of(), null, 5));
        assertEquals(0, r.score());
        assertEquals("未作答", r.remark());
    }

    @Test
    @DisplayName("多选：全对得满分，少选/错选按默认口径得 0")
    void multipleChoice() {
        var full = GradingEngine.grade(req(QuestionType.MC, List.of("A", "C"), List.of("C", "A"), null, 4));
        assertEquals(4, full.score());
        assertTrue(full.correct());

        var subset = GradingEngine.grade(req(QuestionType.MC, List.of("A", "C"), List.of("A"), null, 4));
        assertEquals(0, subset.score(), "默认少选不给分（Q1）");
        assertEquals("少选", subset.remark());

        var extra = GradingEngine.grade(req(QuestionType.MC, List.of("A", "C"), List.of("A", "B", "C"), null, 4));
        assertEquals(0, extra.score());
        assertEquals("错选", extra.remark());
    }

    @Test
    @DisplayName("判断：T/F 与大小写无关")
    void trueFalse() {
        assertEquals(2, GradingEngine.grade(req(QuestionType.TF, List.of("T"), List.of("t"), null, 2)).score());
        assertEquals(0, GradingEngine.grade(req(QuestionType.TF, List.of("T"), List.of("F"), null, 2)).score());
    }

    @Test
    @DisplayName("填空：忽略空格、全角与大小写")
    void blankNormalization() {
        var r1 = GradingEngine.grade(req(QuestionType.BLANK, List.of("二氧化碳"), null, " 二氧化碳 ", 3));
        assertEquals(3, r1.score());

        var r2 = GradingEngine.grade(req(QuestionType.BLANK, List.of("CO2"), null, "ｃｏ２", 3));
        assertEquals(3, r2.score(), "全角转半角 + 忽略大小写");
    }

    @Test
    @DisplayName("填空：同一空多个可接受答案取或关系")
    void blankAlternatives() {
        var r = GradingEngine.grade(req(QuestionType.BLANK, List.of("二氧化碳/CO2/碳酸气"), null, "碳酸气", 3));
        assertEquals(3, r.score());
    }

    @Test
    @DisplayName("填空：多空按命中比例给分")
    void blankMultiBlanks() {
        var full = GradingEngine.grade(req(QuestionType.BLANK, List.of("水", "氧气"), null, "水|氧气", 6));
        assertEquals(6, full.score());
        assertTrue(full.correct());

        var half = GradingEngine.grade(req(QuestionType.BLANK, List.of("水", "氧气"), null, "水|氮气", 6));
        assertEquals(3, half.score());
        assertFalse(half.correct());
        assertEquals("命中 1/2 空", half.remark());
    }

    @Test
    @DisplayName("填空：换行分隔的作答同样识别")
    void blankNewlineSeparated() {
        var r = GradingEngine.grade(req(QuestionType.BLANK, List.of("水", "氧气"), null, "水\n氧气", 6));
        assertEquals(6, r.score());
    }

    @Test
    @DisplayName("主观题：不计分，标记待人工阅卷")
    void essayGoesManual() {
        var r = GradingEngine.grade(req(QuestionType.ESSAY, List.of(), null, "我的回答", 10));
        assertEquals(0, r.score());
        assertFalse(r.correct());
        assertTrue(r.remark().contains("人工阅卷"));
    }

    @Test
    @DisplayName("复合题：本身不计分")
    void compositeNotScored() {
        var r = GradingEngine.grade(req(QuestionType.COMPOSITE, List.of(), null, null, 20));
        assertEquals(0, r.score());
        assertTrue(r.remark().contains("子题"));
    }

    @Test
    @DisplayName("边界：分值为 0 或请求为空时安全返回")
    void invalidInput() {
        assertEquals(0, GradingEngine.grade(req(QuestionType.SC, List.of("A"), List.of("A"), null, 0)).score());
        assertEquals(0, GradingEngine.grade(null).score());
    }
}
