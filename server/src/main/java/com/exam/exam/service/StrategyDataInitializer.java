package com.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.exam.entity.ExamStrategy;
import com.exam.exam.mapper.ExamStrategyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 演示组卷策略（幂等） */
@Slf4j
@Order(5)
@Component
@RequiredArgsConstructor
public class StrategyDataInitializer implements ApplicationRunner {

    private final ExamStrategyMapper strategyMapper;
    private final QuestionBankMapper bankMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (strategyMapper.selectCount(Wrappers.<ExamStrategy>lambdaQuery()) > 0) {
            return;
        }
        QuestionBank bank = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .orderByAsc(QuestionBank::getId)).stream().findFirst().orElse(null);

        ExamStrategy s = new ExamStrategy();
        s.setName("消防安全标准卷（100 分）");
        s.setBankId(bank == null ? null : bank.getId());
        s.setRules("""
                [{"type":"SC","count":4,"score":5},{"type":"MC","count":2,"score":10},\
                {"type":"TF","count":2,"score":5},{"type":"BLANK","count":1,"score":10},\
                {"type":"ESSAY","count":1,"score":40}]""");
        s.setTotalScore(BigDecimal.valueOf(100));
        s.setPassScore(BigDecimal.valueOf(60));
        s.setDurationSec(1800);
        s.setRemark("单选 4×5 + 多选 2×10 + 判断 2×5 + 填空 1×10 + 问答 1×40");
        s.setCreatedAt(LocalDateTime.now());
        strategyMapper.insert(s);

        ExamStrategy quick = new ExamStrategy();
        quick.setName("快速测评卷（50 分）");
        quick.setBankId(bank == null ? null : bank.getId());
        quick.setRules("""
                [{"type":"SC","count":5,"score":6},{"type":"TF","count":2,"score":5},\
                {"type":"BLANK","count":1,"score":10}]""");
        quick.setTotalScore(BigDecimal.valueOf(50));
        quick.setPassScore(BigDecimal.valueOf(30));
        quick.setDurationSec(600);
        quick.setRemark("10 分钟 50 分小测，适合日常抽测");
        quick.setCreatedAt(LocalDateTime.now());
        strategyMapper.insert(quick);

        log.info("seed strategies: {}", List.of(s.getName(), quick.getName()));
    }
}
