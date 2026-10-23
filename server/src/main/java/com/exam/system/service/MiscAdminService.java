package com.exam.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.common.BizException;
import com.exam.system.dto.MiscDtos.*;
import com.exam.system.entity.DictItem;
import com.exam.system.entity.Outline;
import com.exam.system.entity.SysConfig;
import com.exam.system.mapper.DictItemMapper;
import com.exam.system.mapper.OutlineMapper;
import com.exam.system.mapper.SysConfigMapper;
import com.exam.content.mapper.QuestionBankMapper;
import com.exam.content.entity.QuestionBank;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 大纲 / 字典 / 系统参数：三个轻量管理域的通用 CRUD */
@Service
@RequiredArgsConstructor
public class MiscAdminService {

    private final OutlineMapper outlineMapper;
    private final DictItemMapper dictMapper;
    private final SysConfigMapper configMapper;
    private final QuestionBankMapper bankMapper;

    private static String status(String s) {
        return "DISABLED".equals(s) ? "DISABLED" : "ACTIVE";
    }

    // ---------- 大纲 ----------

    /** 大纲行：附带关联题库数，支撑「大纲 → 题库」联动 */
    public record OutlineRow(Long id, String name, String category, String version, String content,
                             String status, Integer sortNo, LocalDateTime createdAt,
                             LocalDateTime updatedAt, int bankCount) {
    }

    public List<OutlineRow> outlines(String keyword, String status) {
        var q = Wrappers.<Outline>lambdaQuery().orderByAsc(Outline::getSortNo).orderByDesc(Outline::getId);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(Outline::getName, keyword).or().like(Outline::getCategory, keyword));
        }
        if (StringUtils.hasText(status)) q.eq(Outline::getStatus, status);
        return outlineMapper.selectList(q).stream()
                .map(o -> new OutlineRow(o.getId(), o.getName(), o.getCategory(), o.getVersion(),
                        o.getContent(), o.getStatus(), o.getSortNo(), o.getCreatedAt(), o.getUpdatedAt(),
                        bankCountOf(o.getId())))
                .toList();
    }

    private int bankCountOf(Long outlineId) {
        Long n = bankMapper.selectCount(Wrappers.<QuestionBank>lambdaQuery()
                .eq(QuestionBank::getOutlineId, outlineId));
        return n == null ? 0 : n.intValue();
    }

    @Transactional
    public Outline createOutline(OutlineUpsert req) {
        Outline o = new Outline();
        applyOutline(o, req);
        o.setCreatedAt(LocalDateTime.now());
        outlineMapper.insert(o);
        return o;
    }

    @Transactional
    public Outline updateOutline(Long id, OutlineUpsert req) {
        Outline o = outlineMapper.selectById(id);
        if (o == null) throw new BizException(404, "大纲不存在");
        applyOutline(o, req);
        o.setUpdatedAt(LocalDateTime.now());
        outlineMapper.updateById(o);
        return o;
    }

    @Transactional
    public void deleteOutline(Long id) {
        if (outlineMapper.selectById(id) == null) throw new BizException(404, "大纲不存在");
        int banks = bankCountOf(id);
        if (banks > 0) {
            throw new BizException("该大纲已关联 " + banks + " 个题库，请先在题库管理中换绑后再删除");
        }
        outlineMapper.deleteById(id);
    }

    private void applyOutline(Outline o, OutlineUpsert req) {
        o.setName(req.name().trim());
        o.setCategory(req.category());
        o.setVersion(req.version());
        o.setContent(req.content());
        o.setStatus(status(req.status()));
        o.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
    }

    // ---------- 字典 ----------

    public List<DictItem> dictItems(String dictType, String keyword) {
        var q = Wrappers.<DictItem>lambdaQuery().orderByAsc(DictItem::getDictType).orderByAsc(DictItem::getSortNo);
        if (StringUtils.hasText(dictType)) q.eq(DictItem::getDictType, dictType);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(DictItem::getLabel, keyword).or().like(DictItem::getCode, keyword));
        }
        return dictMapper.selectList(q);
    }

    /** 字典类型列表（去重） */
    public List<String> dictTypes() {
        return dictMapper.selectList(Wrappers.<DictItem>lambdaQuery()
                        .select(DictItem::getDictType)
                        .groupBy(DictItem::getDictType))
                .stream().map(DictItem::getDictType).distinct().sorted().toList();
    }

    @Transactional
    public DictItem createDict(DictUpsert req) {
        String type = req.dictType().trim().toLowerCase();
        Long dup = dictMapper.selectCount(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .eq(DictItem::getCode, req.code().trim()));
        if (dup != null && dup > 0) {
            throw new BizException("该字典类型下已存在编码：" + req.code().trim());
        }
        DictItem d = new DictItem();
        applyDict(d, req, type);
        d.setCreatedAt(LocalDateTime.now());
        dictMapper.insert(d);
        return d;
    }

    @Transactional
    public DictItem updateDict(Long id, DictUpsert req) {
        DictItem d = dictMapper.selectById(id);
        if (d == null) throw new BizException(404, "字典项不存在");
        String type = req.dictType().trim().toLowerCase();
        Long dup = dictMapper.selectCount(Wrappers.<DictItem>lambdaQuery()
                .eq(DictItem::getDictType, type)
                .eq(DictItem::getCode, req.code().trim())
                .ne(DictItem::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException("该字典类型下已存在编码：" + req.code().trim());
        }
        applyDict(d, req, type);
        dictMapper.updateById(d);
        return d;
    }

    @Transactional
    public void deleteDict(Long id) {
        if (dictMapper.selectById(id) == null) throw new BizException(404, "字典项不存在");
        dictMapper.deleteById(id);
    }

    private void applyDict(DictItem d, DictUpsert req, String type) {
        d.setDictType(type);
        d.setCode(req.code().trim());
        d.setLabel(req.label().trim());
        d.setItemValue(req.value());
        d.setSortNo(req.sortNo() == null ? 0 : req.sortNo());
        d.setStatus(status(req.status()));
        d.setRemark(req.remark());
    }

    // ---------- 系统参数 ----------

    public List<SysConfig> configs(String keyword) {
        var q = Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getParamKey);
        if (StringUtils.hasText(keyword)) {
            q.and(w -> w.like(SysConfig::getParamKey, keyword).or().like(SysConfig::getRemark, keyword));
        }
        return configMapper.selectList(q);
    }

    @Transactional
    public SysConfig createConfig(ConfigUpsert req) {
        String key = req.paramKey().trim();
        Long dup = configMapper.selectCount(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getParamKey, key));
        if (dup != null && dup > 0) {
            throw new BizException("参数键已存在：" + key);
        }
        SysConfig c = new SysConfig();
        c.setParamKey(key);
        c.setParamValue(req.paramValue());
        c.setRemark(req.remark());
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(c);
        return c;
    }

    @Transactional
    public SysConfig updateConfig(Long id, ConfigUpsert req) {
        SysConfig c = configMapper.selectById(id);
        if (c == null) throw new BizException(404, "参数不存在");
        String key = req.paramKey().trim();
        Long dup = configMapper.selectCount(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getParamKey, key)
                .ne(SysConfig::getId, id));
        if (dup != null && dup > 0) {
            throw new BizException("参数键已存在：" + key);
        }
        c.setParamKey(key);
        c.setParamValue(req.paramValue());
        c.setRemark(req.remark());
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(c);
        return c;
    }

    @Transactional
    public void deleteConfig(Long id) {
        if (configMapper.selectById(id) == null) throw new BizException(404, "参数不存在");
        configMapper.deleteById(id);
    }
}
