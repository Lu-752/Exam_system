package com.exam.content.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.common.CsvUtil;
import com.exam.common.ImportResult;
import com.exam.content.dto.Option;
import com.exam.content.dto.QuestionUpsert;
import com.exam.content.dto.QuestionView;
import com.exam.content.entity.Question;
import com.exam.content.entity.QuestionBank;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.mapper.QuestionMapper;
import com.exam.system.entity.Outline;
import com.exam.system.mapper.OutlineMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final QuestionBankMapper bankMapper;
    private final QuestionMapper questionMapper;
    private final OutlineMapper outlineMapper;
    private final ObjectMapper json;

    // ---------- 题库 ----------

    /** 题库视图：附带关联大纲名称 */
    public record BankView(Long id, String name, String level, Long outlineId, String outlineName,
                           Integer sortNo, Boolean recommended, LocalDateTime createdAt) {
    }

    public List<BankView> banks(String keyword) {
        List<QuestionBank> rows = bankMapper.selectList(Wrappers.<QuestionBank>lambdaQuery()
                .like(StringUtils.hasText(keyword), QuestionBank::getName, keyword)
                .orderByDesc(QuestionBank::getSortNo)
                .orderByDesc(QuestionBank::getId));
        Map<Long, String> outlineNames = new HashMap<>();
        return rows.stream().map(b -> new BankView(b.getId(), b.getName(), b.getLevel(),
                b.getOutlineId(), outlineName(b.getOutlineId(), outlineNames),
                b.getSortNo(), b.getRecommended(), b.getCreatedAt())).toList();
    }

    private String outlineName(Long outlineId, Map<Long, String> cache) {
        if (outlineId == null) return null;
        return cache.computeIfAbsent(outlineId, id -> {
            Outline o = outlineMapper.selectById(id);
            return o == null ? null : o.getName();
        });
    }

    @Transactional
    public QuestionBank createBank(String name, String level, Long outlineId) {
        if (!StringUtils.hasText(name)) {
            throw new BizException("题库名称不能为空");
        }
        requireOutline(outlineId);
        QuestionBank b = new QuestionBank();
        b.setName(name.trim());
        b.setLevel(level);
        b.setOutlineId(outlineId);
        b.setSortNo(0);
        b.setRecommended(false);
        b.setCreatedAt(LocalDateTime.now());
        bankMapper.insert(b);
        return b;
    }

    /** 编辑题库：换绑大纲 / 改名 / 改等级 */
    @Transactional
    public QuestionBank updateBank(Long id, String name, String level, Long outlineId) {
        QuestionBank b = bankMapper.selectById(id);
        if (b == null) {
            throw new BizException(404, "题库不存在");
        }
        if (!StringUtils.hasText(name)) {
            throw new BizException("题库名称不能为空");
        }
        requireOutline(outlineId);
        // updateById 会忽略 null 字段导致解绑失败，这里用 UpdateWrapper 显式 set
        bankMapper.update(null, Wrappers.<QuestionBank>lambdaUpdate()
                .eq(QuestionBank::getId, id)
                .set(QuestionBank::getName, name.trim())
                .set(QuestionBank::getLevel, level)
                .set(QuestionBank::getOutlineId, outlineId));
        return bankMapper.selectById(id);
    }

    private void requireOutline(Long outlineId) {
        if (outlineId != null && outlineMapper.selectById(outlineId) == null) {
            throw new BizException("关联的大纲不存在");
        }
    }

    @Transactional
    public void deleteBank(Long id) {
        Long cnt = questionMapper.selectCount(Wrappers.<Question>lambdaQuery().eq(Question::getBankId, id));
        if (cnt != null && cnt > 0) {
            throw new BizException("该题库下还有 " + cnt + " 道试题，请先清空再删除");
        }
        bankMapper.deleteById(id);
    }

    // ---------- 试题 ----------

    public List<QuestionView> questions(Long bankId, String keyword, String type) {
        List<Question> rows = questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .isNull(Question::getParentId)
                .eq(bankId != null, Question::getBankId, bankId)
                .eq(StringUtils.hasText(type), Question::getType, type)
                .like(StringUtils.hasText(keyword), Question::getStem, keyword)
                .orderByDesc(Question::getId));
        return rows.stream().map(this::toView).toList();
    }

    @Transactional
    public QuestionView createQuestion(QuestionUpsert req) {
        if (req.bankId() == null || bankMapper.selectById(req.bankId()) == null) {
            throw new BizException("请选择题库");
        }
        if (!StringUtils.hasText(req.stem())) {
            throw new BizException("题干不能为空");
        }

        boolean composite = "COMPOSITE".equals(req.type());
        if (composite) {
            if (req.children() == null || req.children().isEmpty()) {
                throw new BizException("题冒题至少需要 1 个子题");
            }
            for (QuestionUpsert c : req.children()) {
                if (!StringUtils.hasText(c.stem())) {
                    throw new BizException("子题题干不能为空");
                }
                if ("COMPOSITE".equals(c.type())) {
                    throw new BizException("子题不支持再嵌套题冒题");
                }
                validateAnswer(c);
            }
        } else {
            validateAnswer(req);
        }

        Question q = new Question();
        q.setBankId(req.bankId());
        q.setType(req.type());
        q.setStem(req.stem().trim());
        q.setOptions(writeJson(composite ? List.of() : req.options()));
        q.setAnswer(composite ? "" : req.answer());
        q.setAnalysis(req.analysis());
        q.setScore(composite ? subTotal(req.children()) : (req.score() == null ? BigDecimal.valueOf(5) : req.score()));
        q.setDifficulty(req.difficulty());
        q.setCreatedAt(LocalDateTime.now());
        questionMapper.insert(q);

        if (composite) {
            for (QuestionUpsert c : req.children()) {
                Question sub = new Question();
                sub.setBankId(req.bankId());
                sub.setParentId(q.getId());
                sub.setType(c.type());
                sub.setStem(c.stem().trim());
                sub.setOptions(writeJson(c.options()));
                sub.setAnswer(c.answer());
                sub.setAnalysis(c.analysis());
                sub.setScore(c.score() == null ? BigDecimal.valueOf(5) : c.score());
                sub.setDifficulty(StringUtils.hasText(c.difficulty()) ? c.difficulty() : req.difficulty());
                sub.setCreatedAt(LocalDateTime.now());
                questionMapper.insert(sub);
            }
        }
        return toView(q);
    }

    @Transactional
    public void deleteQuestion(Long id) {
        // 题冒题：连同子题一起删除
        questionMapper.delete(Wrappers.<Question>lambdaQuery().eq(Question::getParentId, id));
        questionMapper.deleteById(id);
    }

    /** 题冒题的子题（按录入顺序） */
    public List<Question> childrenOf(Long parentId) {
        if (parentId == null) {
            return List.of();
        }
        return questionMapper.selectList(Wrappers.<Question>lambdaQuery()
                .eq(Question::getParentId, parentId)
                .orderByAsc(Question::getId));
    }

    public Question requireQuestion(Long id) {
        Question q = questionMapper.selectById(id);
        if (q == null) {
            throw new BizException(404, "试题不存在：" + id);
        }
        return q;
    }

    public List<Question> byIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return questionMapper.selectBatchIds(ids);
    }

    // ---------- 内部 ----------

    private void validateAnswer(QuestionUpsert req) {
        String type = req.type();
        if (type == null) {
            throw new BizException("请选择题型");
        }
        boolean objective = List.of("SC", "MC", "TF", "BLANK").contains(type);
        if (objective && !StringUtils.hasText(req.answer())) {
            throw new BizException("客观题必须填写正确答案");
        }
        if (List.of("SC", "MC").contains(type)) {
            long optionCount = req.options() == null ? 0 : req.options().size();
            if (optionCount < 2) {
                throw new BizException("选择题至少需要 2 个选项");
            }
        }
    }

    private BigDecimal subTotal(List<QuestionUpsert> children) {
        return children.stream()
                .map(c -> c.score() == null ? BigDecimal.valueOf(5) : c.score())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private QuestionView toView(Question q) {
        List<QuestionView> children = "COMPOSITE".equals(q.getType())
                ? childrenOf(q.getId()).stream().map(this::toView).toList()
                : null;
        return new QuestionView(q.getId(), q.getBankId(), q.getParentId(), q.getType(), q.getStem(),
                readJson(q.getOptions()), q.getAnswer() == null ? "" : q.getAnswer(),
                q.getAnalysis(), q.getScore(), children);
    }

    private String writeJson(List<Option> options) {
        try {
            return options == null ? "[]" : json.writeValueAsString(options);
        } catch (Exception e) {
            throw new BizException("选项格式错误");
        }
    }

    private List<Option> readJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            return json.readValue(raw, new TypeReference<List<Option>>() {
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    // ---------------- 批量导入 ----------------

    public static final String TEMPLATE = """
            题型,题干,选项,答案,分值,解析,难度
            单选题,仓库内灭火器应放置在（ ）。,A.随手可取的位置|B.仓库最里侧|C.上锁的柜子|D.无关区域,A,5,取用便捷是初期扑救的关键,简单
            多选题,进入受限空间作业前必须落实（ ）。,A.气体检测|B.强制通风|C.专人监护|D.直接进入,"A,B,C",8,先检测、再通风、全程监护,中等
            判断题,发现有人触电时应先切断电源再施救。,对,5,切断电源是触电急救第一原则,简单
            填空题,干粉灭火器使用口诀中“拔”指的是拔掉____。,保险销,6,拔掉保险销才能按下压把,简单
            简答题,简述初期火灾的处置步骤。,1.报警 2.断电 3.初期扑救 4.疏散,10,按点给分,中等
            """;

    /**
     * CSV 批量导入试题：逐行校验，出错行跳过并回传原因。
     * 列顺序：题型,题干,选项,答案,分值,解析,难度
     */
    @Transactional
    public ImportResult importQuestions(Long bankId, String content) {
        if (bankId == null || requireBank(bankId) == null) {
            throw new BizException("请先选择题库");
        }
        List<List<String>> rows = CsvUtil.parse(content);
        List<ImportResult.RowError> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            throw new BizException("文件内容为空，请先下载模板填写");
        }

        int success = 0;
        int total = 0;
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            if (CsvUtil.isHeader(row, "题型", "题干")) {
                continue;
            }
            total++;
            try {
                String type = mapType(CsvUtil.cell(row, 0));
                // 非选择题常被省略「选项」列：6 列时补一个空列，避免整体错位
                if (!"SC".equals(type) && !"MC".equals(type) && row.size() == 6) {
                    List<String> fixed = new ArrayList<>(row);
                    fixed.add(2, "");
                    row = fixed;
                }
                String stem = CsvUtil.cell(row, 1);
                if (stem.isEmpty()) {
                    throw new BizException("题干不能为空");
                }
                List<Option> options = "SC".equals(type) || "MC".equals(type)
                        ? parseOptions(CsvUtil.cell(row, 2))
                        : List.of();
                if (("SC".equals(type) || "MC".equals(type)) && options.size() < 2) {
                    throw new BizException("选择题至少需要 2 个选项（用 | 分隔）");
                }
                String answer = normalizeAnswer(type, CsvUtil.cell(row, 3));
                if (answer.isEmpty()) {
                    throw new BizException("答案不能为空");
                }
                BigDecimal score = null;
                String scoreRaw = CsvUtil.cell(row, 4);
                if (!scoreRaw.isEmpty()) {
                    try {
                        score = new BigDecimal(scoreRaw);
                    } catch (NumberFormatException ex) {
                        throw new BizException("分值必须是数字：" + scoreRaw);
                    }
                }
                String analysis = CsvUtil.cell(row, 5);
                String difficulty = CsvUtil.cell(row, 6).isEmpty() ? "中等" : CsvUtil.cell(row, 6);

                createQuestion(new QuestionUpsert(bankId, type, stem, options, answer, analysis, score,
                        difficulty, null));
                success++;
            } catch (BizException e) {
                errors.add(new ImportResult.RowError(line, e.getMessage()));
            } catch (Exception e) {
                errors.add(new ImportResult.RowError(line, "导入失败：" + e.getMessage()));
            }
        }
        return ImportResult.of(total, success, errors);
    }

    private QuestionBank requireBank(Long bankId) {
        return bankMapper.selectById(bankId);
    }

    private String mapType(String raw) {
        String t = raw.trim().toUpperCase();
        if (t.contains("多选") || "MC".equals(t)) return "MC";
        if (t.contains("判断") || "TF".equals(t)) return "TF";
        if (t.contains("填空") || "BLANK".equals(t)) return "BLANK";
        if (t.contains("简答") || t.contains("问答") || t.contains("论述") || "ESSAY".equals(t)) return "ESSAY";
        if (t.contains("单选") || "SC".equals(t)) return "SC";
        throw new BizException("无法识别的题型：" + raw);
    }

    private List<Option> parseOptions(String raw) {
        List<Option> list = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return list;
        }
        String[] parts = raw.split("\\|");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i].trim();
            if (s.isEmpty()) {
                continue;
            }
            String key = String.valueOf((char) ('A' + list.size()));
            String text = s;
            if (s.length() > 1 && ".、:：)）".indexOf(s.charAt(1)) >= 0 && Character.isLetter(s.charAt(0))) {
                key = s.substring(0, 1).toUpperCase();
                text = s.substring(2).trim();
            }
            list.add(new Option(key, text));
        }
        return list;
    }

    private String normalizeAnswer(String type, String raw) {
        String a = raw.trim();
        if (a.isEmpty()) {
            return "";
        }
        return switch (type) {
            case "SC" -> a.substring(0, 1).toUpperCase();
            case "MC" -> Arrays.stream(a.split("[,，;；/|\\s]+"))
                    .filter(StringUtils::hasText)
                    .map(x -> x.trim().toUpperCase())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(","));
            case "TF" -> switch (a) {
                case "对", "正确", "是", "T", "TRUE", "√", "1" -> "T";
                default -> "F";
            };
            default -> a;
        };
    }
}
