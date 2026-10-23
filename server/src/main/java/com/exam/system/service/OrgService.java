package com.exam.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.exam.common.BizException;
import com.exam.common.PageResult;
import com.exam.system.dto.OrgDtos.OrgMember;
import com.exam.system.dto.OrgDtos.OrgNode;
import com.exam.system.dto.OrgDtos.OrgStats;
import com.exam.system.dto.OrgDtos.OrgUpsert;
import com.exam.system.entity.Org;
import com.exam.system.entity.SysStudent;
import com.exam.system.mapper.OrgMapper;
import com.exam.system.mapper.SysStudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/** 组织机构：树查询 / 增删改 / 成员列表，与学员档案同源 */
@Service
@RequiredArgsConstructor
public class OrgService {

    private final OrgMapper orgMapper;
    private final SysStudentMapper studentMapper;
    private final JdbcTemplate jdbc;

    // ---------------- 查询 ----------------

    /** 组织树，keyword 命中时保留命中节点 + 其祖先链 */
    public List<OrgNode> tree(String keyword) {
        List<Org> all = orgMapper.selectList(Wrappers.<Org>lambdaQuery()
                .orderByAsc(Org::getSortNo).orderByAsc(Org::getId));
        if (all.isEmpty()) return List.of();

        Map<Long, Integer> direct = memberCountByOrg();
        Map<Long, Integer> total = new HashMap<>(direct);
        Map<Long, Long> parentOf = new HashMap<>();
        for (Org o : all) parentOf.put(o.getId(), o.getParentId());

        // 把直属人数沿父链上卷，得到含下级的累计人数
        for (Org o : all) {
            int c = direct.getOrDefault(o.getId(), 0);
            if (c == 0) continue;
            Long p = o.getParentId();
            int guard = 0;
            while (p != null && guard++ < 64) {
                total.merge(p, c, Integer::sum);
                p = parentOf.get(p);
            }
        }

        Map<Long, OrgNode> index = new LinkedHashMap<>();
        for (Org o : all) {
            index.put(o.getId(), new OrgNode(o.getId(), o.getParentId(), o.getName(), o.getCode(),
                    o.getLeader(), o.getPhone(), o.getStatus(), o.getRemark(), o.getSortNo(),
                    direct.getOrDefault(o.getId(), 0), total.getOrDefault(o.getId(), 0), new ArrayList<>()));
        }

        List<OrgNode> roots = new ArrayList<>();
        for (OrgNode n : index.values()) {
            OrgNode parent = n.parentId() == null ? null : index.get(n.parentId());
            if (parent == null) roots.add(n);
            else parent.children().add(n);
        }

        if (!StringUtils.hasText(keyword)) return roots;
        return prune(roots, keyword.trim().toLowerCase());
    }

    private List<OrgNode> prune(List<OrgNode> nodes, String kw) {
        List<OrgNode> out = new ArrayList<>();
        for (OrgNode n : nodes) {
            if (hit(n, kw)) {
                out.add(n);               // 自身命中，整棵子树保留
                continue;
            }
            List<OrgNode> kids = prune(n.children(), kw);
            if (!kids.isEmpty()) out.add(copy(n, kids));
        }
        return out;
    }

    private boolean hit(OrgNode n, String kw) {
        return contains(n.name(), kw) || contains(n.code(), kw) || contains(n.leader(), kw);
    }

    private boolean contains(String v, String kw) {
        return v != null && v.toLowerCase().contains(kw);
    }

    private OrgNode copy(OrgNode n, List<OrgNode> kids) {
        return new OrgNode(n.id(), n.parentId(), n.name(), n.code(), n.leader(), n.phone(), n.status(),
                n.remark(), n.sortNo(), n.memberCount(), n.totalMemberCount(), kids);
    }

    public OrgStats stats() {
        Integer orgCount = jdbc.queryForObject("SELECT COUNT(*) FROM sys_org", Integer.class);
        Integer assigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_student WHERE org_id IS NOT NULL", Integer.class);
        Integer unassigned = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_student WHERE org_id IS NULL", Integer.class);
        return new OrgStats(nz(orgCount), nz(assigned), nz(unassigned));
    }

    /** 某组织（含下级）的学员，orgId 为空时返回全部 */
    public PageResult<OrgMember> members(Long orgId, String keyword, long page, long size) {
        Set<Long> scope = orgId == null ? null : descendantIds(orgId);

        var q = Wrappers.<SysStudent>lambdaQuery().orderByDesc(SysStudent::getId);
        if (scope != null) {
            if (scope.isEmpty()) return PageResult.of(new Page<>(page, size));
            q.in(SysStudent::getOrgId, scope);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(SysStudent::getName, kw)
                    .or().like(SysStudent::getNo, kw)
                    .or().like(SysStudent::getPhone, kw));
        }

        Page<SysStudent> result = studentMapper.selectPage(new Page<>(page, Math.min(size, 200)), q);
        Page<OrgMember> mapped = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapped.setRecords(result.getRecords().stream()
                .map(s -> new OrgMember(s.getId(), s.getNo(), s.getName(), s.getGender(), s.getPhone(),
                        s.getLevel(), s.getExamCount(), s.getStatus(), s.getOrgName()))
                .toList());
        return PageResult.of(mapped);
    }

    /** 组织自身 + 全部下级 id */
    public Set<Long> descendantIds(Long orgId) {
        List<Org> all = orgMapper.selectList(Wrappers.<Org>lambdaQuery());
        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (Org o : all) {
            childrenOf.computeIfAbsent(o.getParentId() == null ? 0L : o.getParentId(), k -> new ArrayList<>())
                    .add(o.getId());
        }
        Set<Long> ids = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(orgId);
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            if (!ids.add(cur)) continue;
            queue.addAll(childrenOf.getOrDefault(cur, List.of()));
        }
        return ids;
    }

    // ---------------- 写入 ----------------

    @Transactional
    public Org create(OrgUpsert req) {
        String name = req.name().trim();
        Long parentId = req.parentId();
        if (parentId != null && orgMapper.selectById(parentId) == null) {
            throw new BizException("上级组织不存在");
        }
        ensureNameFree(parentId, name, null);

        String code = StringUtils.hasText(req.code()) ? req.code().trim().toUpperCase() : null;
        if (code != null) ensureCodeFree(code, null);

        Org row = new Org();
        row.setParentId(parentId);
        row.setName(name);
        row.setCode(code);
        row.setLeader(req.leader());
        row.setPhone(req.phone());
        row.setStatus(StringUtils.hasText(req.status()) ? req.status() : "ACTIVE");
        row.setRemark(req.remark());
        row.setSortNo(req.sortNo() != null ? req.sortNo() : nextSort(parentId));
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        orgMapper.insert(row);
        return row;
    }

    @Transactional
    public Org update(Long id, OrgUpsert req) {
        Org row = require(id);
        Long parentId = req.parentId();
        if (parentId != null) {
            if (parentId.equals(id)) throw new BizException("不能把组织挂到自己名下");
            if (descendantIds(id).contains(parentId)) {
                throw new BizException("不能把组织移动到自己的下级中");
            }
            if (orgMapper.selectById(parentId) == null) throw new BizException("上级组织不存在");
        }
        String name = req.name().trim();
        ensureNameFree(parentId, name, id);

        if (StringUtils.hasText(req.code())) {
            String code = req.code().trim().toUpperCase();
            if (!code.equals(row.getCode())) {
                ensureCodeFree(code, id);
                row.setCode(code);
            }
        }
        row.setParentId(parentId);
        row.setName(name);
        row.setLeader(req.leader());
        row.setPhone(req.phone());
        if (StringUtils.hasText(req.status())) row.setStatus(req.status());
        row.setRemark(req.remark());
        if (req.sortNo() != null) row.setSortNo(req.sortNo());
        row.setUpdatedAt(LocalDateTime.now());
        orgMapper.updateById(row);
        return row;
    }

    @Transactional
    public void delete(Long id) {
        require(id);
        Long children = orgMapper.selectCount(Wrappers.<Org>lambdaQuery().eq(Org::getParentId, id));
        if (children != null && children > 0) {
            throw new BizException("该组织下还有 " + children + " 个下级组织，请先移除下级");
        }
        Long students = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sys_student WHERE org_id = ?", Long.class, id);
        if (students != null && students > 0) {
            throw new BizException("该组织下还有 " + students + " 名学员，请先调整归属");
        }
        Long users = jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE org_id = ?", Long.class, id);
        if (users != null && users > 0) {
            throw new BizException("该组织下还有 " + users + " 个账号，请先调整归属");
        }
        orgMapper.deleteById(id);
    }

    // ---------------- 内部 ----------------

    private Org require(Long id) {
        Org row = orgMapper.selectById(id);
        if (row == null) throw new BizException(404, "组织不存在");
        return row;
    }

    private void ensureNameFree(Long parentId, String name, Long selfId) {
        var q = Wrappers.<Org>lambdaQuery().eq(Org::getName, name);
        if (parentId == null) q.isNull(Org::getParentId);
        else q.eq(Org::getParentId, parentId);
        if (selfId != null) q.ne(Org::getId, selfId);
        Long dup = orgMapper.selectCount(q);
        if (dup != null && dup > 0) {
            throw new BizException("同级下已存在同名组织：" + name);
        }
    }

    private void ensureCodeFree(String code, Long selfId) {
        var q = Wrappers.<Org>lambdaQuery().eq(Org::getCode, code);
        if (selfId != null) q.ne(Org::getId, selfId);
        Long dup = orgMapper.selectCount(q);
        if (dup != null && dup > 0) {
            throw new BizException("组织编码已存在：" + code);
        }
    }

    private int nextSort(Long parentId) {
        List<Org> siblings = orgMapper.selectList(Wrappers.<Org>lambdaQuery()
                .isNull(parentId == null, Org::getParentId)
                .eq(parentId != null, Org::getParentId, parentId)
                .orderByDesc(Org::getSortNo));
        return siblings.isEmpty() || siblings.get(0).getSortNo() == null
                ? 10 : siblings.get(0).getSortNo() + 10;
    }

    private Map<Long, Integer> memberCountByOrg() {
        Map<Long, Integer> map = new HashMap<>();
        jdbc.query("SELECT org_id, COUNT(*) c FROM sys_student WHERE org_id IS NOT NULL GROUP BY org_id",
                rs -> {
                    map.put(rs.getLong(1), rs.getInt(2));
                });
        return map;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
