package com.exam.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.CsvUtil;
import com.exam.common.ImportResult;
import com.exam.common.PageResult;
import com.exam.system.entity.Org;
import com.exam.system.entity.SysStudent;
import com.exam.system.mapper.OrgMapper;
import com.exam.system.mapper.SysStudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final SysStudentMapper mapper;
    private final OrgMapper orgMapper;
    private final OrgService orgService;

    /**
     * @param orgId 组织 id，按该组织 + 全部下级过滤（优先级高于 org 名称）
     * @param org   组织名称精确匹配，兼容历史调用
     */
    public PageResult<SysStudent> page(String keyword, Long orgId, String org, String status,
                                       long page, long size) {
        LambdaQueryWrapper<SysStudent> q = Wrappers.<SysStudent>lambdaQuery()
                .orderByDesc(SysStudent::getId);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysStudent::getName, kw)
                    .or().like(SysStudent::getNo, kw)
                    .or().like(SysStudent::getPhone, kw));
        }
        if (orgId != null) {
            Set<Long> scope = orgService.descendantIds(orgId);
            if (scope.isEmpty()) {
                return PageResult.of(new Page<>(page, size));
            }
            q.in(SysStudent::getOrgId, scope);
        } else if (StringUtils.hasText(org)) {
            q.eq(SysStudent::getOrgName, org);
        }
        if (StringUtils.hasText(status)) {
            q.eq(SysStudent::getStatus, status);
        }

        Page<SysStudent> result = mapper.selectPage(new Page<>(page, Math.min(size, 200)), q);
        return PageResult.of(result);
    }

    @Transactional
    public SysStudent create(SysStudent req) {
        if (!StringUtils.hasText(req.getNo()) || !StringUtils.hasText(req.getName())) {
            throw new BizException("学号与姓名不能为空");
        }
        Long dup = mapper.selectCount(Wrappers.<SysStudent>lambdaQuery().eq(SysStudent::getNo, req.getNo()));
        if (dup != null && dup > 0) {
            throw new BizException("学号已存在：" + req.getNo());
        }

        SysStudent row = new SysStudent();
        row.setNo(req.getNo().trim());
        row.setName(req.getName().trim());
        row.setGender(req.getGender());
        applyOrg(row, req.getOrgId(), req.getOrgName());
        row.setPhone(req.getPhone());
        row.setLevel(req.getLevel());
        row.setExamCount(0);
        row.setStatus(StringUtils.hasText(req.getStatus()) ? req.getStatus() : "ACTIVE");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        mapper.insert(row);
        return row;
    }

    @Transactional
    public SysStudent update(Long id, SysStudent req) {
        SysStudent row = mapper.selectById(id);
        if (row == null) {
            throw new BizException(404, "学员不存在");
        }
        if (StringUtils.hasText(req.getNo()) && !req.getNo().equals(row.getNo())) {
            Long dup = mapper.selectCount(Wrappers.<SysStudent>lambdaQuery()
                    .eq(SysStudent::getNo, req.getNo())
                    .ne(SysStudent::getId, id));
            if (dup != null && dup > 0) {
                throw new BizException("学号已存在：" + req.getNo());
            }
            row.setNo(req.getNo().trim());
        }
        if (StringUtils.hasText(req.getName())) {
            row.setName(req.getName().trim());
        }
        if (req.getGender() != null) row.setGender(req.getGender());
        if (req.getOrgId() != null || StringUtils.hasText(req.getOrgName())) {
            applyOrg(row, req.getOrgId(), req.getOrgName());
        }
        if (req.getPhone() != null) row.setPhone(req.getPhone());
        if (req.getLevel() != null) row.setLevel(req.getLevel());
        if (req.getStatus() != null) row.setStatus(req.getStatus());
        row.setUpdatedAt(LocalDateTime.now());

        mapper.updateById(row);
        return row;
    }

    @Transactional
    public void delete(Long id) {
        if (mapper.selectById(id) == null) {
            throw new BizException(404, "学员不存在");
        }
        mapper.deleteById(id);
    }

    /** 组织 id 为准，名称从组织表回填，保证两边始终一致 */
    private void applyOrg(SysStudent row, Long orgId, String orgName) {
        if (orgId != null) {
            Org org = orgMapper.selectById(orgId);
            if (org == null) {
                throw new BizException("所选组织不存在");
            }
            row.setOrgId(org.getId());
            row.setOrgName(org.getName());
        } else {
            row.setOrgId(null);
            row.setOrgName(orgName);
        }
    }

    // ---------------- 批量导入 ----------------

    public static final String TEMPLATE = """
            学号,姓名,性别,所属组织,手机号,职业等级,状态
            S20269901,张三,男,消防支队一大队,13800001111,初级,启用
            S20269902,李四,女,综合安全科,13800002222,中级,停用
            """;

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * CSV 批量导入学员。逐行校验，出错行跳过并回传原因，不影响其他行入库。
     *
     * @param autoCreateOrg 组织不存在时是否自动在根组织下创建
     */
    @Transactional
    public ImportResult importCsv(String content, boolean autoCreateOrg) {
        List<List<String>> rows = CsvUtil.parse(content);
        List<ImportResult.RowError> errors = new ArrayList<>();
        if (rows.isEmpty()) {
            throw new BizException("文件内容为空，请先下载模板填写");
        }

        List<Org> orgs = orgMapper.selectList(Wrappers.<Org>lambdaQuery().orderByAsc(Org::getId));
        Map<String, Long> orgIndex = new HashMap<>();
        for (Org o : orgs) {
            orgIndex.put(o.getName(), o.getId());
            if (StringUtils.hasText(o.getCode())) {
                orgIndex.put(o.getCode().toUpperCase(), o.getId());
            }
        }
        Long rootId = orgs.stream().filter(o -> o.getParentId() == null)
                .map(Org::getId).findFirst().orElse(null);

        Set<String> seenNo = new HashSet<>();
        int success = 0;
        int total = 0;

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            int line = i + 1;
            if (CsvUtil.isHeader(row, "学号", "姓名")) {
                continue;
            }
            total++;
            try {
                String no = CsvUtil.cell(row, 0);
                String name = CsvUtil.cell(row, 1);
                if (no.isEmpty() || name.isEmpty()) {
                    throw new BizException("学号与姓名不能为空");
                }
                if (!seenNo.add(no)) {
                    throw new BizException("文件内学号重复：" + no);
                }
                Long dup = mapper.selectCount(Wrappers.<SysStudent>lambdaQuery().eq(SysStudent::getNo, no));
                if (dup != null && dup > 0) {
                    throw new BizException("学号已存在：" + no);
                }

                String phone = CsvUtil.cell(row, 4);
                if (!phone.isEmpty() && !PHONE.matcher(phone).matches()) {
                    throw new BizException("手机号格式不正确：" + phone);
                }

                String orgRaw = CsvUtil.cell(row, 3);
                if (orgRaw.isEmpty()) {
                    throw new BizException("所属组织不能为空");
                }
                Long orgId = orgIndex.get(orgRaw);
                if (orgId == null) {
                    orgId = orgIndex.get(orgRaw.toUpperCase());
                }
                if (orgId == null) {
                    if (!autoCreateOrg) {
                        throw new BizException("组织不存在：" + orgRaw + "（可在组织机构中先创建）");
                    }
                    Org o = new Org();
                    o.setParentId(rootId);
                    o.setName(orgRaw);
                    o.setStatus("ACTIVE");
                    o.setSortNo(90);
                    o.setCreatedAt(LocalDateTime.now());
                    o.setUpdatedAt(LocalDateTime.now());
                    orgMapper.insert(o);
                    orgId = o.getId();
                    orgIndex.put(orgRaw, orgId);
                }
                final Long targetOrgId = orgId;
                Org org = orgs.stream().filter(x -> x.getId().equals(targetOrgId)).findFirst()
                        .orElseGet(() -> orgMapper.selectById(targetOrgId));

                SysStudent s = new SysStudent();
                s.setNo(no);
                s.setName(name);
                s.setGender(normalizeGender(CsvUtil.cell(row, 2)));
                s.setOrgId(targetOrgId);
                s.setOrgName(org == null ? orgRaw : org.getName());
                s.setPhone(phone.isEmpty() ? null : phone);
                s.setLevel(CsvUtil.cell(row, 5).isEmpty() ? "初级" : CsvUtil.cell(row, 5));
                s.setExamCount(0);
                s.setStatus(normalizeStatus(CsvUtil.cell(row, 6)));
                s.setCreatedAt(LocalDateTime.now());
                s.setUpdatedAt(LocalDateTime.now());
                mapper.insert(s);
                success++;
            } catch (BizException e) {
                errors.add(new ImportResult.RowError(line, e.getMessage()));
            } catch (Exception e) {
                errors.add(new ImportResult.RowError(line, "导入失败：" + e.getMessage()));
            }
        }
        return ImportResult.of(total, success, errors);
    }

    private String normalizeGender(String raw) {
        if ("男".equals(raw) || "M".equalsIgnoreCase(raw) || "male".equalsIgnoreCase(raw)) {
            return "男";
        }
        if ("女".equals(raw) || "F".equalsIgnoreCase(raw) || "female".equalsIgnoreCase(raw)) {
            return "女";
        }
        return raw.isEmpty() ? null : raw;
    }

    private String normalizeStatus(String raw) {
        return switch (raw) {
            case "停用", "禁用", "DISABLED" -> "DISABLED";
            default -> "ACTIVE";
        };
    }
}
