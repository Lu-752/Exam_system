<template>
  <div v-if="q.type === 'SC'" class="opts">
    <label v-for="o in q.options" :key="o.key" class="opt" :class="{ picked: modelValue === o.key }">
      <input type="radio" :name="`q${q.id}`" :value="o.key" :checked="modelValue === o.key"
             @change="emit('update:modelValue', o.key)" />
      <span class="okey">{{ o.key }}</span>
      <span class="otext">{{ o.text }}</span>
    </label>
  </div>

  <div v-else-if="q.type === 'MC'" class="opts">
    <label v-for="o in q.options" :key="o.key" class="opt" :class="{ picked: picked.includes(o.key) }">
      <input type="checkbox" :value="o.key" :checked="picked.includes(o.key)"
             @change="toggle(o.key)" />
      <span class="okey">{{ o.key }}</span>
      <span class="otext">{{ o.text }}</span>
    </label>
  </div>

  <div v-else-if="q.type === 'TF'" class="opts tf">
    <label class="opt" :class="{ picked: modelValue === 'T' }">
      <input type="radio" :name="`q${q.id}`" value="T" :checked="modelValue === 'T'"
             @change="emit('update:modelValue', 'T')" />
      <span class="okey">√</span><span class="otext">正确</span>
    </label>
    <label class="opt" :class="{ picked: modelValue === 'F' }">
      <input type="radio" :name="`q${q.id}`" value="F" :checked="modelValue === 'F'"
             @change="emit('update:modelValue', 'F')" />
      <span class="okey">×</span><span class="otext">错误</span>
    </label>
  </div>

  <div v-else-if="q.type === 'BLANK'" class="blank">
    <el-input :model-value="modelValue" size="large"
              placeholder="多个空用 | 分隔，例如：提、拔、握、压"
              @update:model-value="(v: string) => emit('update:modelValue', v)" />
  </div>

  <div v-else class="blank">
    <el-input :model-value="modelValue" type="textarea" :rows="rows"
              placeholder="请输入作答内容，可分点作答"
              @update:model-value="(v: string) => emit('update:modelValue', v)" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ExamQuestion } from '@/api/exam'

const props = withDefaults(defineProps<{ q: ExamQuestion; modelValue: string; rows?: number }>(), {
  rows: 8
})
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()

/** 多选题答案统一以逗号串保存，便于整卷提交 */
const picked = computed(() => (props.modelValue ? props.modelValue.split(',').filter(Boolean) : []))

function toggle(key: string) {
  const next = picked.value.includes(key)
    ? picked.value.filter((k) => k !== key)
    : [...picked.value, key].sort()
  emit('update:modelValue', next.join(','))
}
</script>

<style scoped>
.opts { display: flex; flex-direction: column; gap: 10px; }
.opts.tf { flex-direction: row; }
.opts.tf .opt { flex: 1; }
.opt {
  display: flex; align-items: center; gap: 12px;
  padding: 13px 16px;
  border: 1px solid var(--border);
  border-radius: 10px;
  cursor: pointer;
  transition: all .14s;
  background: #fff;
}
.opt:hover { border-color: #b9cbff; background: #f7faff; }
.opt.picked {
  border-color: var(--brand);
  background: var(--brand-soft);
  box-shadow: 0 0 0 3px rgba(61, 110, 245, .1);
}
.opt input { margin: 0; accent-color: var(--brand); width: 15px; height: 15px; }
.okey { font-weight: 700; color: var(--brand); width: 16px; font-size: calc(var(--qfs, 15px) - 1px); }
.otext { font-size: calc(var(--qfs, 15px) - 1px); line-height: 1.7; color: var(--text); }
.blank { max-width: 780px; }
.blank :deep(.el-textarea__inner) { font-size: calc(var(--qfs, 15px) - 1px); line-height: 1.9; }
</style>
