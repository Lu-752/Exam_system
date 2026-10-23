package com.exam.exam.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import com.exam.exam.entity.ExamStrategy;
import com.exam.exam.entity.Paper;
import com.exam.exam.entity.PaperItem;
import com.exam.exam.mapper.ExamStrategyMapper;
import com.exam.exam.mapper.PaperItemMapper;
import com.exam.exam.mapper.PaperMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 组卷策略：保存题型配比规则，一键按策略生成试卷 */
@Service
@RequiredArgsConstructor
public class StrategyService {

    private final ExamStrategyMapper strategyMapper;
    private final QuestionMapper questionMapper;
    private final QuestionBankMapper bankMapper;
    private final PaperMapper paperMapper;
    private final PaperItemMapper itemMapper;
    private final ObjectMapper json;

    public record Rule(String type, int count, BigDecimal score) {
    }

    public record StrategyRow(Long id, String name, Long bankId, String bankName, List<Rule> rules,
                              BigDecimal totalScore, BigDecimal passScore, int durationSec,
                              String remark, int questionCount, LocalDateTime createdAt) {
    }

    public record SaveReq(String name, Long bankId, List<Rule> rules, BigDecimal passScore,
                          Integer durationSec, String remark) {
    }

    public record GenerateResult(Long paperId, String paperName, BigDecimal totalScore,
                                 int questionCount, List<String> gaps) {
    }

    public List<StrategyRow> list() {
        List<ExamStrategy> list = strategyMapper.selectList(Wrappers.<ExamStrategy>lambdaQuery()
                .orderByDesc(ExamStrategy::getId));
        Map<Long, QuestionBank> banks = new HashMap<>();
        List<StrategyRow> rows = new ArrayList<>();
        for (ExamStrategy s : list) {
            QuestionBank bank = s.getBankId() == null ? null
                    : banks.computeIfAbsent(s.getBankId(), bankMapper::selectById);
            List<Rule> rules = readRules(s.getRules());
            int count = rules.stream().mapToInt(Rule::count).sum();
            rows.add(new StrategyRow(s.getId(), s.getName(), s.getBankId(),
                    bank == null ? "全部题库" : bank.getName(), rules,
                    s.getTotalScore(), s.getPassScore(),
                    s.getDurationSec() == null ? 1800 : s.getDurationSec(),
                    s.getRemark(), count, s.getCreatedAt()));
        }
        return rows;
    }

    @Transactional
    public StrategyRow save(Long id, SaveReq req) {
        if (!StringUtils.hasText(req.name())) {
            throw new BizException("策略名称不能为空");
        }
        if (req.rules() == null || req.rules().isEmpty()) {
            throw new BizException("请至少配置一条题型规则");
        }
        for (Rule r : req.rules()) {
            if (r.count() <= 0) throw new BizException("题数必须大于 0");
            if (r.score() == null || r.score().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException("每题分值必须大于 0");
            }
        }

        BigDecimal total = req.rules().stream()
                .map(r -> r.score().multiply(BigDecimal.valueOf(r.count())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ExamStrategy s = id == null ? new ExamStrategy() : strategyMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "策略不存在");
        }
        s.setName(req.name().trim());
        s.setBankId(req.bankId());
        s.setRules(writeRules(req.rules()));
        s.setTotalScore(total);
        s.setPassScore(req.passScore() == null ? BigDecimal.valueOf(60) : req.passScore());
        s.setDurationSec(req.durationSec() == null ? 1800 : req.durationSec());
        s.setRemark(req.remark());

        if (id == null) {
            s.setCreatedAt(LocalDateTime.now());
            strategyMapper.insert(s);
        } else {
            strategyMapper.updateById(s);
        }
        return list().stream().filter(r -> r.id().equals(s.getId())).findFirst()
                .orElseThrow(() -> new BizException(500, "保存后读取失败"));
    }

    @Transactional
    public void delete(Long id) {
        strategyMapper.deleteById(id);
    }

    /** 按策略抽题生成试卷（题库不足时按实际数量生成并回传缺口） */
    @Transactional
    public GenerateResult generate(Long id, String paperName) {
        ExamStrategy s = strategyMapper.selectById(id);
        if (s == null) {
            throw new BizException(404, "策略不存在");
        }
        List<Rule> rules = readRules(s.getRules());
        if (rules.isEmpty()) {
            throw new BizException("策略未配置题型规则");
        }

        List<String> gaps = new ArrayList<>();
        List<Question> picked = new ArrayList<>();
        List<BigDecimal> scores = new ArrayList<>();

        for (Rule rule : rules) {
            var q = Wrappers.<Question>lambdaQuery()
                    .isNull(Question::getParentId)
                    .eq(Question::getType, rule.type());
            if (s.getBankId() != null) q.eq(Question::getBankId, s.getBankId());
            List<Question> pool = new ArrayList<>(questionMapper.selectList(q));
            Collections.shuffle(pool);

            int need = rule.count();
            if (pool.size() < need) {
                gaps.add(typeLabel(rule.type()) + "需要 " + need + " 题，题库仅有 " + pool.size() + " 题");
            }
            for (Question question : pool.subList(0, Math.min(need, pool.size()))) {
                picked.add(question);
                scores.add(rule.score());
            }
        }

        if (picked.isEmpty()) {
            throw new BizException("题库中没有可用试题，无法生成试卷");
        }

        BigDecimal total = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        String name = StringUtils.hasText(paperName)
                ? paperName.trim()
                : s.getName() + " · " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMdd HHmm"));

        Paper paper = new Paper();
        paper.setName(name);
        paper.setBankId(s.getBankId());
        paper.setTotalScore(total);
        paper.setPassScore(s.getPassScore());
        paper.setCreatedAt(LocalDateTime.now());
        paperMapper.insert(paper);

        int sort = 0;
        for (int i = 0; i < picked.size(); i++) {
            PaperItem item = new PaperItem();
            item.setPaperId(paper.getId());
            item.setQuestionId(picked.get(i).getId());
            item.setSortNo(++sort);
            item.setScore(scores.get(i));
            itemMapper.insert(item);
        }

        return new GenerateResult(paper.getId(), name, total, picked.size(), gaps);
    }

    // ---------- 内部 ----------

    private List<Rule> readRules(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<Rule>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeRules(List<Rule> rules) {
        try {
            return json.writeValueAsString(rules);
        } catch (Exception e) {
            throw new BizException(500, "规则序列化失败");
        }
    }

    private String typeLabel(String type) {
        return switch (type) {
            case "SC" -> "单选题";
            case "MC" -> "多选题";
            case "TF" -> "判断题";
            case "BLANK" -> "填空题";
            case "ESSAY" -> "问答题";
            default -> type;
        };
    }
}
