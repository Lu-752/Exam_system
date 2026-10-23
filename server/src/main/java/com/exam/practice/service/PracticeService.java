package com.exam.practice.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.content.dto.Option;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.grading.GradeRequest;
import com.exam.content.grading.GradingEngine;
import com.exam.content.grading.QuestionType;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import com.exam.practice.entity.PracticeRecord;
import com.exam.practice.mapper.PracticeRecordMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** 练习中心：按题库刷题、即时判分、错题本 */
@Service
@RequiredArgsConstructor
public class PracticeService {

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final PracticeRecordMapper recordMapper;
    private final ObjectMapper json;

    public record BankCard(Long id, String name, String level, int questionCount,
                           int practicedCount, int correctCount, BigDecimal correctRate) {
    }

    public record PracticeQuestion(Long id, Long bankId, String type, String stem,
                                   List<Option> options, BigDecimal score) {
    }

    public record AnswerResult(boolean correct, String remark, String myAnswer, String correctAnswer,
                               String analysis, BigDecimal fullScore) {
    }

    public record WrongItem(Long questionId, String type, String stem, List<Option> options,
                            String myAnswer, String correctAnswer, String analysis,
                            int wrongTimes, String lastAt) {
    }

    public List<BankCard> banks(Long userId) {
        List<QuestionBank> banks = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .orderByAsc(QuestionBank::getSortNo).orderByAsc(QuestionBank::getId));

        List<PracticeRecord> records = records(userId);
        Map<Long, int[]> agg = new HashMap<>();   // bankId -> [practiced, correct]
        for (PracticeRecord r : records) {
            if (r.getBankId() == null) continue;
            int[] a = agg.computeIfAbsent(r.getBankId(), k -> new int[2]);
            a[0]++;
            if (Boolean.TRUE.equals(r.getCorrect())) a[1]++;
        }

        List<BankCard> out = new ArrayList<>();
        for (QuestionBank b : banks) {
            long total = questionMapper.selectCount(practicePool(b.getId()));
            int[] a = agg.getOrDefault(b.getId(), new int[2]);
            BigDecimal rate = a[0] == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(a[1] * 100.0 / a[0]).setScale(1, RoundingMode.HALF_UP);
            out.add(new BankCard(b.getId(), b.getName(), b.getLevel(), (int) total, a[0], a[1], rate));
        }
        return out;
    }

    /** 抽题：随机顺序，可限定只练错题 */
    public List<PracticeQuestion> questions(Long userId, Long bankId, int size, boolean onlyWrong) {
        if (size <= 0) size = 10;
        size = Math.min(size, 50);

        List<Question> pool;
        if (onlyWrong) {
            Set<Long> wrongIds = wrongQuestionIds(userId);
            if (wrongIds.isEmpty()) return List.of();
            pool = questionMapper.selectList(Wrappers.<Question>lambdaQuery().in(Question::getId, wrongIds));
        } else if (bankId != null) {
            pool = questionMapper.selectList(practicePool(bankId));
        } else {
            pool = questionMapper.selectList(practicePool(null));
        }

        List<Question> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);
        return shuffled.stream().limit(size)
                .map(q -> new PracticeQuestion(q.getId(), q.getBankId(), q.getType(), q.getStem(),
                        readOptions(q.getOptions()), q.getScore()))
                .toList();
    }

    /** 练习用题池：不含题冒题及其子题（练习只做单题即时判分） */
    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question> practicePool(Long bankId) {
        return Wrappers.<Question>lambdaQuery()
                .isNull(Question::getParentId)
                .ne(Question::getType, "COMPOSITE")
                .eq(bankId != null, Question::getBankId, bankId);
    }

    /** 提交单题练习：即时判分 + 落练习记录 */
    @Transactional
    public AnswerResult answer(Long userId, Long questionId, String answer) {
        Question q = questionMapper.selectById(questionId);
        if (q == null) {
            throw new BizException(404, "试题不存在");
        }
        QuestionType type = parseType(q.getType());
        if (type == QuestionType.ESSAY || type == QuestionType.COMPOSITE) {
            throw new BizException("练习模式暂不支持主观题（请到考试中作答）");
        }

        GradeRequest.GradeResult gr = GradingEngine.grade(new GradeRequest(
                type, correctList(type, q.getAnswer()), splitSelected(type, answer),
                answer, q.getScore() == null ? 0 : q.getScore().doubleValue()));

        PracticeRecord rec = new PracticeRecord();
        rec.setUserId(userId);
        rec.setQuestionId(q.getId());
        rec.setBankId(q.getBankId());
        rec.setMyAnswer(answer);
        rec.setCorrect(gr.correct());
        rec.setCreatedAt(LocalDateTime.now());
        recordMapper.insert(rec);

        return new AnswerResult(gr.correct(), gr.remark(), answer, q.getAnswer(), q.getAnalysis(),
                q.getScore() == null ? BigDecimal.ZERO : q.getScore());
    }

    /** 错题本：同一题按最近一次错误聚合 */
    public List<WrongItem> wrongBook(Long userId) {
        List<PracticeRecord> records = records(userId);
        Map<Long, PracticeRecord> latestWrong = new LinkedHashMap<>();
        Map<Long, Boolean> lastResult = new HashMap<>();
        Map<Long, Integer> times = new HashMap<>();

        for (PracticeRecord r : records) {
            lastResult.put(r.getQuestionId(), Boolean.TRUE.equals(r.getCorrect()));
        }
        for (PracticeRecord r : records) {
            if (Boolean.TRUE.equals(r.getCorrect())) continue;
            times.merge(r.getQuestionId(), 1, Integer::sum);
            PracticeRecord prev = latestWrong.get(r.getQuestionId());
            if (prev == null || r.getCreatedAt().isAfter(prev.getCreatedAt())) {
                latestWrong.put(r.getQuestionId(), r);
            }
        }

        List<WrongItem> out = new ArrayList<>();
        for (Map.Entry<Long, PracticeRecord> e : latestWrong.entrySet()) {
            // 最近一次已答对则从错题本移出
            if (Boolean.TRUE.equals(lastResult.get(e.getKey()))) continue;
            Question q = questionMapper.selectById(e.getKey());
            if (q == null) continue;
            out.add(new WrongItem(q.getId(), q.getType(), q.getStem(), readOptions(q.getOptions()),
                    e.getValue().getMyAnswer(), q.getAnswer(), q.getAnalysis(),
                    times.getOrDefault(q.getId(), 1), e.getValue().getCreatedAt().toString().replace('T', ' ')));
        }
        out.sort((a, b) -> b.lastAt().compareTo(a.lastAt()));
        return out;
    }

    @Transactional
    public void clearWrong(Long userId, Long questionId) {
        recordMapper.delete(Wrappers.<PracticeRecord>lambdaQuery()
                .eq(PracticeRecord::getUserId, userId)
                .eq(PracticeRecord::getQuestionId, questionId)
                .eq(PracticeRecord::getCorrect, false));
    }

    // ---------- 内部 ----------

    private List<PracticeRecord> records(Long userId) {
        return recordMapper.selectList(Wrappers.<PracticeRecord>lambdaQuery()
                .eq(PracticeRecord::getUserId, userId)
                .orderByAsc(PracticeRecord::getCreatedAt));
    }

    private Set<Long> wrongQuestionIds(Long userId) {
        Set<Long> ids = new LinkedHashSet<>();
        records(userId).stream().filter(r -> !Boolean.TRUE.equals(r.getCorrect()))
                .forEach(r -> ids.add(r.getQuestionId()));
        return ids;
    }

    private QuestionType parseType(String raw) {
        try {
            return QuestionType.valueOf(raw);
        } catch (Exception e) {
            return QuestionType.SC;
        }
    }

    private List<String> correctList(QuestionType type, String answer) {
        if (!StringUtils.hasText(answer)) return List.of();
        return switch (type) {
            case MC -> Arrays.stream(answer.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            case BLANK -> Arrays.stream(answer.split("\\|")).map(String::trim).toList();
            default -> List.of(answer.trim());
        };
    }

    private List<String> splitSelected(QuestionType type, String answer) {
        if (!StringUtils.hasText(answer)) return List.of();
        return switch (type) {
            case MC -> Arrays.stream(answer.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
            default -> List.of(answer);
        };
    }

    private List<Option> readOptions(String raw) {
        if (!StringUtils.hasText(raw)) return List.of();
        try {
            return json.readValue(raw, new TypeReference<List<Option>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }
}
