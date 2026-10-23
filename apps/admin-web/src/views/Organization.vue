<template>
  <div class="page-head">
    <div class="page-title">组织机构</div>
    <div class="head-actions">
      <el-button @click="reload">刷新</el-button>
      <el-button type="primary" @click="openCreate(null)">新增根组织</el-button>
    </div>
  </div>

  <div class="org-layout">
    <!-- 左：组织树 -->
    <div class="card org-tree-card">
      <div class="card-head">
        <span class="card-title">组织架构</span>
        <span class="card-date">{{ stats.orgCount }} 个组织</span>
      </div>

      <el-input v-model="keyword" placeholder="搜索组织 / 编码 / 负责人" clearable size="small"
                class="tree-search" @input="onFilter" />

      <div v-loading="loading" class="tree-wrap">
        <el-tree
          ref="treeRef"
          :data="tree"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :filter-node-method="filterNode"
          :props="{ label: 'name', children: 'children' }"
          @node-click="selectOrg"
        >
          <template #default="{ data }">
            <div class="tnode" :class="{ off: data.status === 'DISABLED' }">
              <span class="tname" :title="data.name">{{ data.name }}</span>
              <span v-if="data.code" class="tcode">{{ data.code }}</span>
              <span class="tcount" :title="`直属 ${data.memberCount} 人 / 含下级 ${data.totalMemberCount} 人`">
                {{ data.children?.length ? data.totalMemberCount : data.memberCount }}
              </span>
              <span class="tops">
                <el-button link size="small" title="新增下级" @click.stop="openCreate(data)">＋</el-button>
                <el-button link size="small" title="编辑" @click.stop="openEdit(data)">✎</el-button>
                <el-button link size="small" type="danger" title="删除" @click.stop="remove(data)">✕</el-button>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!loading && !tree.length" description="还没有组织，先新增一个根组织" :image-size="72" />
      </div>
    </div>

    <!-- 右：组织详情 + 成员 -->
    <div class="card org-detail">
      <template v-if="current">
        <div class="detail-head">
          <div>
            <div class="dname">
              {{ current.name }}
              <el-tag v-if="current.status === 'DISABLED'" size="small" type="info" effect="plain">已停用</el-tag>
            </div>
            <div class="dpath">{{ pathOf(current) }}</div>
          </div>
          <div class="dhead-actions">
            <el-button size="small" @click="openCreate(current)">新增下级</el-button>
            <el-button size="small" @click="openEdit(current)">编辑</el-button>
            <el-button size="small" type="danger" plain @click="remove(current)">删除</el-button>
          </div>
        </div>

        <div class="meta">
          <div class="m-item"><span class="m-k">组织编码</span><span class="m-v">{{ current.code || '—' }}</span></div>
          <div class="m-item"><span class="m-k">负责人</span><span class="m-v">{{ current.leader || '—' }}</span></div>
          <div class="m-item"><span class="m-k">联系电话</span><span class="m-v">{{ current.phone || '—' }}</span></div>
          <div class="m-item"><span class="m-k">直属成员</span><span class="m-v">{{ current.memberCount }} 人</span></div>
          <div class="m-item"><span class="m-k">含下级</span><span class="m-v">{{ current.totalMemberCount }} 人</span></div>
          <div class="m-item"><span class="m-k">下级组织</span><span class="m-v">{{ current.children?.length || 0 }} 个</span></div>
          <div v-if="current.remark" class="m-item wide"><span class="m-k">备注</span><span class="m-v">{{ current.remark }}</span></div>
        </div>

        <div class="member-head">
          <span class="card-title">成员（含下级）· {{ memberTotal }} 人</span>
          <div class="member-tools">
            <el-input v-model="memberKeyword" placeholder="搜索姓名 / 学号 / 手机号" clearable size="small"
                      style="width: 210px" @keyup.enter="searchMembers" @clear="searchMembers" />
            <el-button size="small" @click="searchMembers">查询</el-button>
            <el-button size="small" type="primary" plain @click="addMember">添加成员</el-button>
          </div>
        </div>

        <el-table v-loading="memberLoading" :data="members" border stripe size="default"
                  empty-text="该组织下暂无成员" @row-dblclick="jumpMember">
          <el-table-column prop="no" label="学号" width="130" />
          <el-table-column prop="name" label="姓名" width="110" />
          <el-table-column prop="gender" label="性别" width="70" align="center" />
          <el-table-column prop="orgName" label="直属组织" min-width="150" show-overflow-tooltip />
          <el-table-column prop="phone" label="手机号" width="130" />
          <el-table-column prop="level" label="等级" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small" effect="light">
                {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <span class="pager-info">双击成员可跳到学员管理维护档案</span>
          <el-pagination v-model:current-page="memberPage" v-model:page-size="memberSize" :total="memberTotal"
                         :page-sizes="[10, 20, 50]" layout="sizes, prev, pager, next"
                         background small @current-change="loadMembers" @size-change="searchMembers" />
        </div>
      </template>

      <el-empty v-else description="选择左侧组织查看详情与成员" />
    </div>
  </div>

  <!-- 新增 / 编辑 -->
  <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑组织' : '新增组织'" width="540px"
             :close-on-click-modal="false" @closed="resetForm">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
      <el-form-item label="上级组织">
        <el-tree-select v-model="form.parentId" :data="tree" node-key="id" check-strictly clearable
                        :props="{ label: 'name', children: 'children' }" placeholder="不选则为根组织"
                        style="width: 100%" />
      </el-form-item>
      <el-form-item label="组织名称" prop="name">
        <el-input v-model="form.name" placeholder="如 消防支队二大队" maxlength="40" show-word-limit />
      </el-form-item>
      <el-form-item label="组织编码">
        <el-input v-model="form.code" placeholder="如 FIRE-02（选填，全局唯一）" maxlength="32" />
      </el-form-item>
      <el-form-item label="负责人">
        <el-input v-model="form.leader" placeholder="选填" maxlength="32" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.phone" placeholder="选填" maxlength="32" />
      </el-form-item>
      <el-form-item label="排序号">
        <el-input-number v-model="form.sortNo" :min="0" :max="9999" :step="10" controls-position="right" />
        <span class="form-tip">数字越小越靠前</span>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="form.status">
          <el-radio value="ACTIVE">启用</el-radio>
          <el-radio value="DISABLED">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit
                  placeholder="职责说明（选填）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialog.visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import type { ElTree } from 'element-plus'
import {
  createOrg, deleteOrg, flattenOrgs, getOrgStats, listOrgMembers, listOrgs, updateOrg,
  type OrgMember, type OrgNode, type OrgPayload, type OrgStats
} from '@/api/org'

const router = useRouter()

const tree = ref<OrgNode[]>([])
const flat = ref<OrgNode[]>([])
const current = ref<OrgNode | null>(null)
const stats = ref<OrgStats>({ orgCount: 0, memberCount: 0, unassigned: 0 })
const loading = ref(false)
const keyword = ref('')
const treeRef = ref<InstanceType<typeof ElTree>>()

const members = ref<OrgMember[]>([])
const memberTotal = ref(0)
const memberPage = ref(1)
const memberSize = ref(10)
const memberKeyword = ref('')
const memberLoading = ref(false)

const dialog = reactive<{ visible: boolean; id: number | null }>({ visible: false, id: null })
const saving = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<Required<Pick<OrgPayload, 'name'>> & OrgPayload>({
  name: '', code: '', leader: '', phone: '', status: 'ACTIVE', remark: '', sortNo: 10, parentId: null
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入组织名称', trigger: 'blur' }]
}

function onFilter() {
  treeRef.value?.filter(keyword.value)
}

function filterNode(value: string, data: OrgNode) {
  if (!value) return true
  const kw = value.trim().toLowerCase()
  return [data.name, data.code, data.leader].some((v) => (v || '').toLowerCase().includes(kw))
}

/** 面包屑路径 */
function pathOf(node: OrgNode) {
  const names = [node.name]
  let p = node.parentId
  let guard = 0
  while (p && guard++ < 32) {
    const hit = flat.value.find((o) => o.id === p)
    if (!hit) break
    names.unshift(hit.name)
    p = hit.parentId
  }
  return names.join(' / ')
}

async function reload() {
  loading.value = true
  try {
    tree.value = await listOrgs()
    flat.value = flattenOrgs(tree.value)
    stats.value = await getOrgStats()

    const keepId = current.value?.id
    const keep = keepId ? flat.value.find((o) => o.id === keepId) : null
    const target = keep ?? flat.value[0]
    if (target) {
      current.value = target
      await nextTick()
      treeRef.value?.setCurrentKey(target.id)
      await loadMembers()
    } else {
      current.value = null
      members.value = []
      memberTotal.value = 0
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function selectOrg(node: OrgNode) {
  if (current.value?.id === node.id) return
  current.value = node
  memberKeyword.value = ''
  memberPage.value = 1
  await loadMembers()
}

async function loadMembers() {
  if (!current.value) return
  memberLoading.value = true
  try {
    const res = await listOrgMembers(current.value.id, {
      keyword: memberKeyword.value, page: memberPage.value, size: memberSize.value
    })
    members.value = res.records
    memberTotal.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    memberLoading.value = false
  }
}

function searchMembers() {
  memberPage.value = 1
  loadMembers()
}

function jumpMember(row: OrgMember) {
  router.push({ path: '/students', query: { keyword: row.no } })
}

function addMember() {
  if (!current.value) return
  router.push({ path: '/students', query: { orgId: String(current.value.id), new: '1' } })
}

function openCreate(parent: OrgNode | null) {
  dialog.id = null
  Object.assign(form, {
    name: '', code: '', leader: '', phone: '', status: 'ACTIVE', remark: '',
    sortNo: 10, parentId: parent?.id ?? null
  })
  dialog.visible = true
}

function openEdit(node: OrgNode) {
  dialog.id = node.id
  Object.assign(form, {
    name: node.name, code: node.code ?? '', leader: node.leader ?? '', phone: node.phone ?? '',
    status: node.status, remark: node.remark ?? '', sortNo: node.sortNo ?? 10,
    parentId: node.parentId ?? null
  })
  dialog.visible = true
}

function resetForm() {
  formRef.value?.clearValidate()
}

async function save() {
  const ok = await formRef.value?.validate().catch(() => false)
  if (!ok) return
  saving.value = true
  try {
    const payload: OrgPayload = { ...form }
    if (dialog.id) {
      await updateOrg(dialog.id, payload)
      ElMessage.success('已保存')
    } else {
      const created = await createOrg(payload)
      ElMessage.success('组织已创建')
      current.value = created as OrgNode
    }
    dialog.visible = false
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(node: OrgNode) {
  try {
    await ElMessageBox.confirm(
      `确定删除组织「${node.name}」吗？有下级组织或成员时无法删除。`, '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteOrg(node.id)
    ElMessage.success('已删除')
    if (current.value?.id === node.id) current.value = null
    await reload()
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(reload)
</script>

<style scoped>
.org-layout { display: flex; gap: 12px; align-items: flex-start; }
.org-tree-card { width: 340px; flex: 0 0 auto; }
.org-detail { flex: 1; min-width: 0; }

.tree-search { margin-bottom: 8px; }
.tree-wrap { min-height: 240px; }
.tnode { display: flex; align-items: center; gap: 6px; width: 100%; padding-right: 4px; font-size: 12.5px; }
.tnode.off .tname { color: var(--text-3); text-decoration: line-through; }
.tname { color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tcode { font-size: 10.5px; color: var(--text-3); background: #f4f6fa; border-radius: 3px; padding: 0 4px; }
.tcount {
  margin-left: auto; flex: 0 0 auto; font-size: 11px; color: var(--brand);
  background: var(--brand-soft); border-radius: 8px; padding: 0 6px; line-height: 16px;
}
.tops { display: none; flex: 0 0 auto; gap: 0; }
.tnode:hover .tops { display: inline-flex; }
.tnode:hover .tcount { display: none; }
.tops :deep(.el-button) { margin-left: 0; padding: 0 3px; font-size: 12px; }

.detail-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.dname { font-size: 16px; font-weight: 600; color: var(--text); display: flex; align-items: center; gap: 8px; }
.dpath { margin-top: 4px; font-size: 12px; color: var(--text-3); }
.dhead-actions { flex: 0 0 auto; display: flex; gap: 6px; }

.meta {
  display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px 16px;
  margin: 14px 0 4px; padding: 12px 14px;
  background: #fafbfe; border: 1px solid var(--border); border-radius: 8px;
}
.m-item { display: flex; flex-direction: column; gap: 2px; }
.m-item.wide { grid-column: 1 / -1; }
.m-k { font-size: 11.5px; color: var(--text-3); }
.m-v { font-size: 13px; color: var(--text); }

.member-head { display: flex; align-items: center; gap: 12px; margin: 16px 0 10px; }
.member-tools { margin-left: auto; display: flex; align-items: center; gap: 8px; }

.pager { display: flex; align-items: center; margin-top: 12px; }
.pager-info { color: var(--text-3); font-size: 12px; }
.pager :deep(.el-pagination) { margin-left: auto; }

.form-tip { margin-left: 10px; font-size: 12px; color: var(--text-3); }

@media (max-width: 1100px) {
  .org-layout { flex-direction: column; }
  .org-tree-card { width: 100%; }
}
</style>
