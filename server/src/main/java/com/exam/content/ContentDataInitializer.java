package com.exam.content;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.entity.ExamPlan;
import com.exam.exam.entity.Paper;
import com.exam.exam.entity.PaperItem;
import com.exam.exam.mapper.ExamPlanMapper;
import com.exam.exam.mapper.PaperItemMapper;
import com.exam.exam.mapper.PaperMapper;
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
 * 内容与考试演示数据（幂等）：1 个题库 + 10 道六种题型 + 1 张试卷 + 1 场进行中的考试。
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class ContentDataInitializer implements ApplicationRunner {

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final PaperMapper paperMapper;
    private final PaperItemMapper itemMapper;
    private final ExamPlanMapper planMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bankMapper.selectCount(Wrappers.<QuestionBank>lambdaQuery()) > 0) {
            return;
        }

        QuestionBank bank = new QuestionBank();
        bank.setName("消防安全基础题库");
        bank.setLevel("初级");
        bank.setSortNo(5);
        bank.setRecommended(true);
        bank.setCreatedAt(LocalDateTime.now());
        bankMapper.insert(bank);

        List<Question> questions = List.of(
                q(bank.getId(), "SC", "手提式干粉灭火器压力表指针在（ ）区域时表示压力正常。",
                        opts("A", "红色区", "B", "绿色区", "C", "黄色区", "D", "黑色区"), "B",
                        "指针在绿色区表示压力正常，红色区为压力不足，黄色区为压力过高。", "简单"),
                q(bank.getId(), "SC", "扑救电气火灾时，应优先选用（ ）。",
                        opts("A", "清水", "B", "泡沫灭火器", "C", "干粉或二氧化碳灭火器", "D", "酸碱灭火器"), "C",
                        "电气火灾严禁用水和泡沫，防止触电。", "简单"),
                q(bank.getId(), "SC", "高处作业是指坠落高度基准面（ ）及以上有可能坠落的高处进行的作业。",
                        opts("A", "1m", "B", "2m", "C", "3m", "D", "5m"), "B",
                        "GB/T 3608 规定高处作业为 2m 及以上。", "简单"),
                q(bank.getId(), "SC", "发现有人触电，第一步应当（ ）。",
                        opts("A", "直接用手拉开触电者", "B", "立即切断电源或用绝缘物挑开电线", "C", "先打 120", "D", "泼水降温"), "B",
                        "必须先脱离电源，否则施救者也会触电。", "简单"),
                q(bank.getId(), "MC", "下列属于特种作业的有（ ）。",
                        opts("A", "电工作业", "B", "高处作业", "C", "焊接与热切割作业", "D", "文员办公"), "A,B,C",
                        "文员办公不属于特种作业目录范围。", "中等"),
                q(bank.getId(), "MC", "进入受限空间作业前必须落实的措施有（ ）。",
                        opts("A", "检测氧含量与有毒有害气体", "B", "强制通风", "C", "专人监护", "D", "直接进入抢修"), "A,B,C",
                        "受限空间作业必须先检测、再通风、全程监护。", "中等"),
                q(bank.getId(), "TF", "发现有人触电时，应先切断电源再进行施救。", "[]", "T",
                        "正确。切断电源是触电急救的第一原则。", "简单"),
                q(bank.getId(), "TF", "灭火器可以倒置使用以加快喷射。", "[]", "F",
                        "错误。倒置会破坏虹吸结构，部分灭火器无法正常喷射。", "简单"),
                q(bank.getId(), "BLANK", "干粉灭火器使用口诀“提、拔、握、压”中，“拔”指的是拔掉____。", "[]", "保险销/保险销钉",
                        "拔掉保险销后才能按下压把喷射。", "简单"),
                q(bank.getId(), "ESSAY", "简述电气火灾的处置步骤（不少于 3 点）。", "[]", "",
                        "参考要点：①切断电源；②用干粉或二氧化碳灭火器扑救；③报警并疏散；④禁止用水扑救；⑤保护现场配合调查。", "中等")
        );
        questions.forEach(questionMapper::insert);

        Paper paper = new Paper();
        paper.setName("消防安全综合卷 A");
        paper.setBankId(bank.getId());
        paper.setTotalScore(BigDecimal.valueOf(questions.size() * 10L));
        paper.setPassScore(BigDecimal.valueOf(60));
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int sort = 1;
        for (Question q : questions) {
            PaperItem item = new PaperItem();
            item.setPaperId(paper.getId());
            item.setQuestionId(q.getId());
            item.setSortNo(sort++);
            item.setScore(BigDecimal.TEN);
            itemMapper.insert(item);
        }

        ExamPlan plan = new ExamPlan();
        plan.setName("2026 年度消防安全知识竞赛（演示）");
        plan.setPaperId(paper.getId());
        plan.setStartAt(LocalDateTime.now().minusDays(1));
        plan.setEndAt(LocalDateTime.now().plusDays(60));
        plan.setDurationSec(1800);
        plan.setPassScore(BigDecimal.valueOf(60));
        plan.setMaxAttempts(1);
        plan.setStatus("PUBLISHED");
        plan.setCreatedAt(LocalDateTime.now());
        planMapper.insert(plan);

        log.info("seeded content: bank={}, questions={}, paper={}, plan={}",
                bank.getId(), questions.size(), paper.getId(), plan.getId());
    }

    private Question q(Long bankId, String type, String stem, String options, String answer,
                       String analysis, String difficulty) {
        Question x = new Question();
        x.setBankId(bankId);
        x.setType(type);
        x.setStem(stem);
        x.setOptions(options);
        x.setAnswer(answer);
        x.setAnalysis(analysis);
        x.setScore(BigDecimal.TEN);
        x.setDifficulty(difficulty);
        x.setCreatedAt(LocalDateTime.now());
        return x;
    }

    private String opts(String... kv) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"key\":\"").append(kv[i]).append("\",\"text\":\"").append(kv[i + 1]).append("\"}");
        }
        return sb.append(']').toString();
    }
}
