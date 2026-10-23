package com.exam.system;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.exam.system.entity.Org;
import com.exam.system.mapper.OrgMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 组织架构种子数据 + 历史学员的组织归属回填（幂等）。
 */
@Slf4j
@Order(3)
@Component
@RequiredArgsConstructor
public class OrgDataInitializer implements ApplicationRunner {

    private final OrgMapper orgMapper;
    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orgMapper.selectCount(Wrappers.<Org>lambdaQuery()) == 0) {
            seed();
            log.info("seeded demo org tree");
        }
        backfillStudents();
    }

    private void seed() {
        Long root = insert(null, "培训中心", "HQ", "王建国", "0731-88880000", "全市安全培训与考核归口管理部门", 10);

        Long fire = insert(root, "消防支队一大队", "FIRE-01", "李强", "0731-88880101", "负责消防设施操作与应急处置培训", 10);
        insert(fire, "消防一中队", "FIRE-01-A", "张勇", "0731-88880111", null, 10);
        insert(fire, "消防二中队", "FIRE-01-B", "刘洋", "0731-88880112", null, 20);

        insert(root, "电工车间 A 组", "ELEC-A", "陈明", "0731-88880201", "低压电工作业实操训练", 20);
        insert(root, "危化品仓储中心", "HAZ-01", "赵敏", "0731-88880301", "危险化学品仓储与装卸作业", 30);
        insert(root, "高处作业培训基地", "HIGH-01", "周涛", "0731-88880401", "登高架设与高处安装维护", 40);
        insert(root, "综合安全科", "SAFE-01", "孙丽", "0731-88880501", "综合安全管理与考核组织", 50);
    }

    private Long insert(Long parentId, String name, String code, String leader, String phone,
                        String remark, int sortNo) {
        Org o = new Org();
        o.setParentId(parentId);
        o.setName(name);
        o.setCode(code);
        o.setLeader(leader);
        o.setPhone(phone);
        o.setStatus("ACTIVE");
        o.setRemark(remark);
        o.setSortNo(sortNo);
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        orgMapper.insert(o);
        return o.getId();
    }

    /** 历史上只存了 org_name，按名称对齐到 org_id，保证两边数据一致 */
    private void backfillStudents() {
        var orgs = orgMapper.selectList(Wrappers.<Org>lambdaQuery());
        int filled = 0;
        for (Org o : orgs) {
            filled += jdbc.update(
                    "UPDATE sys_student SET org_id = ? WHERE org_id IS NULL AND org_name = ?",
                    o.getId(), o.getName());
        }
        if (filled > 0) {
            log.info("backfilled {} students to org_id", filled);
        }
    }
}
