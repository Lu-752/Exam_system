package com.exam.content;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 题冒题演示数据（幂等）：材料 + 单选子题 + 问答子题，总分子题累加。
 */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor
public class CompositeDataInitializer implements ApplicationRunner {

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long exists = questionMapper.selectCount(Wrappers.<Question>lambdaQuery()
                .eq(Question::getType, "COMPOSITE"));
        if (exists != null && exists > 0) {
            return;
        }
        List<QuestionBank> banks = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .orderByAsc(QuestionBank::getId));
        if (banks.isEmpty()) {
            return;
        }
        QuestionBank bank = banks.get(0);

        Question parent = new Question();
        parent.setBankId(bank.getId());
        parent.setType("COMPOSITE");
        parent.setStem("【案例材料】2026 年 3 月，某商场三层餐饮区后厨发生油锅起火。"
                + "现场员工先用锅盖覆盖未果，随后取用干粉灭火器扑救，约 3 分钟后才拨打 119 报警。"
                + "请结合材料回答下列问题。");
        parent.setOptions("[]");
        parent.setScore(BigDecimal.valueOf(20));
        parent.setAnalysis("本题为材料题，按子题累加计分。");
        parent.setDifficulty("中等");
        parent.setCreatedAt(LocalDateTime.now());
        questionMapper.insert(parent);

        insertChild(bank.getId(), parent.getId(), "SC",
                "该起油锅火灾最初应判定为（ ）类火灾。",
                "[{\"key\":\"A\",\"text\":\"A 类（固体物质）\"},"
                        + "{\"key\":\"B\",\"text\":\"B 类（液体或可熔化固体）\"},"
                        + "{\"key\":\"C\",\"text\":\"C 类（气体）\"},"
                        + "{\"key\":\"D\",\"text\":\"D 类（金属）\"}]",
                "B", "食用油属于液体可燃物，初期按 B 类火灾处置。", BigDecimal.valueOf(5));

        insertChild(bank.getId(), parent.getId(), "ESSAY",
                "请指出材料中员工处置不当之处，并说明餐饮后厨油锅起火的正确处置顺序。",
                "[]", "",
                "参考要点：①未先关闭燃气/电源与排烟，火势易沿烟道蔓延；②锅盖覆盖未果后仍强行靠近，未使用灭火毯；"
                        + "③报警过晚，应先报警再扑救；④正确顺序为关火断电→锅盖或灭火毯覆盖窒息→干粉/湿布覆盖→"
                        + "禁止泼水→疏散并报 119。",
                BigDecimal.valueOf(15));

        log.info("seeded composite question: id={}, children=2, total=20", parent.getId());
    }

    private void insertChild(Long bankId, Long parentId, String type, String stem, String options,
                             String answer, String analysis, BigDecimal score) {
        Question q = new Question();
        q.setBankId(bankId);
        q.setParentId(parentId);
        q.setType(type);
        q.setStem(stem);
        q.setOptions(options);
        q.setAnswer(answer);
        q.setAnalysis(analysis);
        q.setScore(score);
        q.setDifficulty("中等");
        q.setCreatedAt(LocalDateTime.now());
        questionMapper.insert(q);
    }
}
