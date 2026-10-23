package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.DictItem;
import com.exam.system.entity.Outline;
import com.exam.system.entity.SysConfig;
import com.exam.system.mapper.DictItemMapper;
import com.exam.system.mapper.OutlineMapper;
import com.exam.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 演示种子：字典 / 系统参数 / 大纲（各自幂等，空才插） */
@Slf4j
@Order(4)
@Component
@RequiredArgsConstructor
public class MiscDataInitializer implements ApplicationRunner {

    private final DictItemMapper dictMapper;
    private final SysConfigMapper configMapper;
    private final OutlineMapper outlineMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedDict();
        seedConfig();
        seedOutline();
    }

    private void seedDict() {
        if (dictMapper.selectCount(Wrappers.<DictItem>lambdaQuery()) > 0) return;
        long now = System.currentTimeMillis();
        List<DictItem> items = List.of(
                dict("grade", "junior", "初级", "1", 1, now),
                dict("grade", "middle", "中级", "2", 2, now),
                dict("grade", "senior", "高级", "3", 3, now),
                dict("difficulty", "easy", "简单", "1", 1, now),
                dict("difficulty", "normal", "中等", "2", 2, now),
                dict("difficulty", "hard", "困难", "3", 3, now));
        items.forEach(dictMapper::insert);
        log.info("seed dict: grade/difficulty");
    }

    private DictItem dict(String type, String code, String label, String value, int sort, long now) {
        DictItem d = new DictItem();
        d.setDictType(type);
        d.setCode(code);
        d.setLabel(label);
        d.setItemValue(value);
        d.setSortNo(sort);
        d.setStatus("ACTIVE");
        d.setCreatedAt(new java.sql.Timestamp(now).toLocalDateTime());
        return d;
    }

    private void seedConfig() {
        if (configMapper.selectCount(Wrappers.<SysConfig>lambdaQuery()) > 0) return;
        config("site.name", "安全生产培训考试系统", "站点名称，登录页与顶栏展示");
        config("site.copyright", "© 2026 培训中心", "页脚版权");
        config("proctor.switchWarn", "3", "切屏预警阈值（次），达到即标记异常答卷");
        log.info("seed configs: 3 items");
    }

    private void config(String key, String value, String remark) {
        SysConfig c = new SysConfig();
        c.setParamKey(key);
        c.setParamValue(value);
        c.setRemark(remark);
        c.setUpdatedAt(LocalDateTime.now());
        configMapper.insert(c);
    }

    private void seedOutline() {
        if (outlineMapper.selectCount(Wrappers.<Outline>lambdaQuery()) > 0) return;
        Outline o = new Outline();
        o.setName("消防安全培训大纲");
        o.setCategory("消防");
        o.setVersion("V2.1");
        o.setContent("""
                1. 消防基础知识与法规要求
                2. 初起火灾扑救与灭火器使用
                3. 疏散逃生与应急组织
                4. 典型火灾案例分析""");
        o.setStatus("ACTIVE");
        o.setSortNo(1);
        o.setCreatedAt(LocalDateTime.now());
        outlineMapper.insert(o);

        Outline e = new Outline();
        e.setName("低压电工作业培训大纲");
        e.setCategory("电工");
        e.setVersion("V1.3");
        e.setContent("""
                1. 电工安全基础知识
                2. 低压电器与线路安装
                3. 接地与漏电保护
                4. 触电急救""");
        e.setStatus("ACTIVE");
        e.setSortNo(2);
        e.setCreatedAt(LocalDateTime.now());
        outlineMapper.insert(e);
        log.info("seed outlines: 2 items");
    }
}
