<template>
  <div class="memo-page">
    <AppMenu />
    <header class="memo-hero">
      <div>
        <p class="eyebrow">Memo workspace</p>
        <h1>手动管理是基础，AI 负责帮你理解指令并执行备忘录操作。</h1>
        <p class="hero-copy">
          现在的 AI 入口不再只是识别时间，而是支持新增、删除、查询、修改和完成备忘录。
        </p>
      </div>
      <div class="hero-actions">
        <button class="solid-btn" @click="openCreateModal">手动新建</button>
      </div>
    </header>

    <section class="quick-card">
      <div class="quick-header">
        <div>
          <h2>AI 指令区</h2>
          <p>例如：帮我删除“买教材”、把“明天交作业”改成“周五晚上交作业”、查看开会相关备忘录。</p>
        </div>
        <span class="cheap-badge">便宜模型 + 规则增强</span>
      </div>
      <textarea
        v-model="aiInstruction"
        class="quick-input"
        placeholder="输入一句自然语言，让 AI 处理备忘录的增删查改"
      />
      <div class="quick-actions">
        <button class="ghost-btn" :disabled="parseLoading" @click="handleAiParse">
          {{ parseLoading ? '解析中...' : 'AI 解析指令' }}
        </button>
        <button class="solid-btn" :disabled="saveLoading" @click="openCreateModal">
          手动创建
        </button>
      </div>
    </section>

    <section class="toolbar">
      <div class="tabs">
        <button
          v-for="item in tabs"
          :key="item.value"
          :class="['tab-btn', statusTab === item.value ? 'active' : '']"
          @click="changeTab(item.value)"
        >
          {{ item.label }}
        </button>
      </div>
      <div class="toolbar-right">
        <label class="check-wrap">
          <input v-model="hasRemindOnly" type="checkbox" @change="handleFilterChange" />
          <span>只看有提醒</span>
        </label>
        <input
          v-model="keyword"
          class="search-input"
          :placeholder="semanticActive ? '可继续普通搜索，或点语义搜索更新结果' : '搜索标题或内容'"
          @keydown.enter="handleKeywordSearch"
        />
        <button class="ghost-btn compact" @click="handleKeywordSearch">搜索</button>
        <button
          class="solid-btn compact semantic-btn"
          :disabled="semanticLoading"
          @click="handleSemanticSearch"
        >
          {{ semanticLoading ? '语义检索中...' : '语义搜索' }}
        </button>
      </div>
    </section>

    <section class="memo-grid">
      <article class="summary-card">
        <h3>当前摘要</h3>
        <div class="summary-row">
          <strong>{{ stats.total }}</strong>
          <span>全部</span>
        </div>
        <div class="summary-row">
          <strong>{{ stats.todo }}</strong>
          <span>待办</span>
        </div>
        <div class="summary-row">
          <strong>{{ stats.done }}</strong>
          <span>已完成</span>
        </div>
        <div class="summary-row">
          <strong>{{ stats.remind }}</strong>
          <span>有提醒</span>
        </div>
      </article>

      <div class="memo-list-wrap">
        <div v-if="semanticActive" class="search-banner">
          <div>
            <strong>当前为语义搜索结果</strong>
            <p>查询词：{{ semanticQuery }}</p>
          </div>
          <button class="ghost-btn compact" @click="clearSemanticSearch">返回列表</button>
        </div>
        <div v-if="listLoading" class="empty-state">正在加载备忘录...</div>
        <div v-else-if="!memoList.length" class="empty-state">还没有匹配的备忘录。</div>
        <div v-else class="memo-list">
          <article
            v-for="memo in memoList"
            :key="memo.id"
            :class="['memo-card', memo.status === 1 ? 'done' : '', isOverdue(memo) ? 'overdue' : '']"
          >
            <div class="memo-top">
              <div>
                <h3>{{ memo.title }}</h3>
                <p class="meta-line">
                  <span class="badge" :class="memo.status === 1 ? 'done' : 'todo'">
                    {{ memo.status === 1 ? '已完成' : '待办' }}
                  </span>
                  <span v-if="memo.source === 1" class="badge ai">AI 辅助</span>
                  <span v-if="isOverdue(memo)" class="badge warn">已过提醒</span>
                </p>
              </div>
              <button class="icon-btn" @click="openEditModal(memo)">编辑</button>
            </div>

            <p class="memo-content">{{ memo.content }}</p>

            <div class="memo-times">
              <span>提醒：{{ formatDateTime(memo.remindTime) }}</span>
              <span>更新：{{ formatDateTime(memo.updatedTime) }}</span>
            </div>

            <div class="memo-actions">
              <button class="ghost-btn compact" @click="toggleStatus(memo)">
                {{ memo.status === 1 ? '恢复' : '完成' }}
              </button>
              <button class="ghost-btn compact" @click="openEditModal(memo)">修改</button>
              <button class="danger-btn compact" @click="handleDelete(memo)">删除</button>
            </div>
          </article>
        </div>

        <div v-if="!semanticActive" class="pager">
          <button class="ghost-btn compact" :disabled="page <= 1" @click="fetchMemos(page - 1)">
            上一页
          </button>
          <span>第 {{ page }} / {{ totalPages }} 页</span>
          <button class="ghost-btn compact" :disabled="page >= totalPages" @click="fetchMemos(page + 1)">
            下一页
          </button>
        </div>
      </div>
    </section>

    <div v-if="showAiConfirm" class="modal-mask" @click.self="showAiConfirm = false">
      <div class="modal-card wide-card">
        <h3>AI 操作预览</h3>
        <p class="hint strong">{{ aiSummary || 'AI 已解析完毕，请确认是否执行。' }}</p>

        <div v-if="queryResults.length" class="query-box">
          <h4>查询结果</h4>
          <ul>
            <li v-for="item in queryResults" :key="item.id">
              <strong>{{ item.title }}</strong>
              <span>{{ item.content }}</span>
            </li>
          </ul>
        </div>

        <div v-if="unresolvedList.length" class="query-box unresolved-box">
          <h4>未解决项</h4>
          <ul>
            <li v-for="item in unresolvedList" :key="item">{{ item }}</li>
          </ul>
        </div>

        <div v-if="pendingOps.length" class="ops-list">
          <div v-for="(op, index) in pendingOps" :key="index" class="op-card">
            <div class="op-head">
              <span class="badge ai">{{ actionText(op.action) }}</span>
              <button class="icon-btn compact" @click="pendingOps.splice(index, 1)">移除</button>
            </div>
            <p v-if="op.memo?.title"><strong>{{ op.memo.title }}</strong></p>
            <p v-if="op.memo?.content">{{ op.memo.content }}</p>
            <p v-if="op.memo?.remindTime" class="mini-line">提醒：{{ formatDateTime(op.memo.remindTime) }}</p>
            <p v-if="op.message" class="mini-line">{{ op.message }}</p>
          </div>
        </div>

        <div class="modal-actions">
          <button class="ghost-btn" @click="showAiConfirm = false">关闭</button>
          <button class="solid-btn" :disabled="!pendingOps.length || saveLoading" @click="confirmAiOps">
            {{ saveLoading ? '执行中...' : `确认执行（${pendingOps.length}）` }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="showEditModal" class="modal-mask" @click.self="showEditModal = false">
      <div class="modal-card">
        <h3>{{ editDraft.id ? '修改备忘录' : '手动新建备忘录' }}</h3>
        <label>
          标题
          <input v-model="editDraft.title" class="field" placeholder="标题可为空，系统会自动补齐" />
        </label>
        <label>
          内容
          <textarea v-model="editDraft.content" class="field textarea" placeholder="请输入备忘内容"></textarea>
        </label>
        <label>
          提醒时间
          <input v-model="editDraft.remindTime" type="datetime-local" class="field" />
        </label>
        <div class="modal-actions">
          <button class="ghost-btn" @click="showEditModal = false">取消</button>
          <button class="solid-btn" :disabled="saveLoading" @click="saveEditDraft">
            {{ saveLoading ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import AppMenu from '@/components/AppMenu.vue'
import request from '@/utils/request'

type MemoItem = {
  id: number
  title: string
  content: string
  remindTime: string | null
  updatedTime?: string | null
  createdTime?: string | null
  source: number
  status: number
}

type MemoOp = {
  action: string
  matchedId?: number
  memo?: Partial<MemoItem>
  message?: string
}

const tabs = [
  { label: '全部', value: 'all' },
  { label: '待办', value: 'todo' },
  { label: '已完成', value: 'done' },
]

const aiInstruction = ref('')
const keyword = ref('')
const statusTab = ref('all')
const hasRemindOnly = ref(false)
const memoList = ref<MemoItem[]>([])
const page = ref(1)
const pageSize = ref(8)
const total = ref(0)
const semanticActive = ref(false)
const semanticLoading = ref(false)
const semanticQuery = ref('')

const parseLoading = ref(false)
const saveLoading = ref(false)
const listLoading = ref(false)
const showAiConfirm = ref(false)
const showEditModal = ref(false)

const aiSummary = ref('')
const pendingOps = ref<MemoOp[]>([])
const unresolvedList = ref<string[]>([])
const queryResults = ref<MemoItem[]>([])

const editDraft = reactive({
  id: null as number | null,
  title: '',
  content: '',
  remindTime: ''
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const stats = computed(() => {
  const totalCount = memoList.value.length
  const todo = memoList.value.filter((item) => item.status !== 1).length
  const done = memoList.value.filter((item) => item.status === 1).length
  const remind = memoList.value.filter((item) => item.remindTime).length
  return { total: totalCount, todo, done, remind }
})

function toDateTimeLocal(value?: string | null) {
  if (!value) return ''
  return value.slice(0, 16)
}

function formatDateTime(value?: string | null) {
  if (!value) return '未设置'
  return value.replace('T', ' ').slice(0, 16)
}

function isOverdue(memo: MemoItem) {
  if (!memo.remindTime || memo.status === 1) return false
  return new Date(memo.remindTime).getTime() < Date.now()
}

function actionText(action: string) {
  const map: Record<string, string> = {
    CREATE: '新增',
    UPDATE: '修改',
    DELETE: '删除',
    COMPLETE: '完成',
    QUERY: '查询'
  }
  return map[action] ?? action
}

function applyClientFilters(items: MemoItem[]) {
  return items.filter((item) => {
    const statusMatch =
      statusTab.value === 'all'
        ? true
        : statusTab.value === 'done'
          ? item.status === 1
          : item.status !== 1
    const remindMatch = hasRemindOnly.value ? Boolean(item.remindTime) : true
    return statusMatch && remindMatch
  })
}

async function fetchMemos(targetPage = page.value) {
  listLoading.value = true
  try {
    page.value = targetPage
    const res: any = await request.get('/memo', {
      params: {
        page: page.value,
        size: pageSize.value,
        status: statusTab.value,
        keyword: keyword.value.trim() || undefined,
        hasRemind: hasRemindOnly.value ? 1 : undefined,
      },
    })
    if (res.success) {
      memoList.value = res.data ?? []
      total.value = res.total ?? 0
    }
  } finally {
    listLoading.value = false
  }
}

async function runSemanticSearch(query: string) {
  listLoading.value = true
  semanticLoading.value = true
  try {
    const res: any = await request.get('/memo/search', {
      params: { q: query },
    })
    if (res.success) {
      page.value = 1
      semanticActive.value = true
      semanticQuery.value = query
      memoList.value = applyClientFilters(res.data ?? [])
      total.value = memoList.value.length
    }
  } finally {
    listLoading.value = false
    semanticLoading.value = false
  }
}

async function refreshCurrentList(resetToFirstPage = false) {
  if (semanticActive.value && semanticQuery.value) {
    await runSemanticSearch(semanticQuery.value)
    return
  }
  await fetchMemos(resetToFirstPage ? 1 : page.value)
}

function changeTab(value: string) {
  statusTab.value = value
  if (semanticActive.value && semanticQuery.value) {
    runSemanticSearch(semanticQuery.value)
    return
  }
  fetchMemos(1)
}

function handleFilterChange() {
  if (semanticActive.value && semanticQuery.value) {
    runSemanticSearch(semanticQuery.value)
    return
  }
  fetchMemos(1)
}

function handleKeywordSearch() {
  semanticActive.value = false
  semanticQuery.value = ''
  fetchMemos(1)
}

function handleSemanticSearch() {
  const query = keyword.value.trim()
  if (!query) {
    window.alert('请输入语义搜索内容')
    return
  }
  runSemanticSearch(query)
}

function clearSemanticSearch() {
  semanticActive.value = false
  semanticQuery.value = ''
  fetchMemos(1)
}

function openCreateModal() {
  editDraft.id = null
  editDraft.title = ''
  editDraft.content = aiInstruction.value
  editDraft.remindTime = ''
  showEditModal.value = true
}

function openEditModal(memo: MemoItem) {
  editDraft.id = memo.id
  editDraft.title = memo.title
  editDraft.content = memo.content
  editDraft.remindTime = toDateTimeLocal(memo.remindTime)
  showEditModal.value = true
}

async function saveEditDraft() {
  if (!editDraft.content.trim()) {
    window.alert('内容不能为空')
    return
  }
  saveLoading.value = true
  try {
    const payload = {
      title: editDraft.title.trim(),
      content: editDraft.content.trim(),
      remindTime: editDraft.remindTime || null,
      source: 0,
    }
    const res: any = editDraft.id
      ? await request.put(`/memo/${editDraft.id}`, payload)
      : await request.post('/memo', payload)
    if (res.success) {
      showEditModal.value = false
      aiInstruction.value = ''
      await refreshCurrentList(!editDraft.id)
    }
  } finally {
    saveLoading.value = false
  }
}

async function handleAiParse() {
  if (!aiInstruction.value.trim()) {
    window.alert('指令不能为空')
    return
  }
  parseLoading.value = true
  try {
    const res: any = await request.post('/memo/ai-parse', {
      instruction: aiInstruction.value.trim(),
    })
    if (res.success) {
      aiSummary.value = res.data.summary ?? ''
      pendingOps.value = res.data.ops ?? []
      unresolvedList.value = res.data.unresolved ?? []
      queryResults.value = res.data.queryResults ?? []
      showAiConfirm.value = true
    }
  } finally {
    parseLoading.value = false
  }
}

async function confirmAiOps() {
  if (!pendingOps.value.length) return
  saveLoading.value = true
  try {
    const res: any = await request.post('/memo/ai-confirm', { ops: pendingOps.value })
    if (res.success) {
      showAiConfirm.value = false
      aiInstruction.value = ''
      await refreshCurrentList(true)
    }
  } finally {
    saveLoading.value = false
  }
}

async function toggleStatus(memo: MemoItem) {
  const url = memo.status === 1 ? `/memo/${memo.id}/uncomplete` : `/memo/${memo.id}/complete`
  const res: any = await request.put(url)
  if (res.success) {
    await refreshCurrentList()
  }
}

async function handleDelete(memo: MemoItem) {
  if (!window.confirm(`确认删除「${memo.title}」吗？`)) return
  const res: any = await request.delete(`/memo/${memo.id}`)
  if (res.success) {
    await refreshCurrentList()
  }
}

onMounted(() => {
  fetchMemos(1)
})
</script>

<style scoped>
.memo-page {
  min-height: 100vh;
  padding: 72px 32px 32px;
  background:
    radial-gradient(circle at top left, rgba(237, 125, 49, 0.18), transparent 28%),
    radial-gradient(circle at top right, rgba(33, 150, 243, 0.18), transparent 25%),
    linear-gradient(180deg, #f7f2e8 0%, #f5f7fb 52%, #eef3f8 100%);
  color: #1f2937;
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

.memo-hero,
.quick-card,
.toolbar,
.memo-grid {
  width: min(1180px, 100%);
  margin: 0 auto 22px;
}

.memo-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: flex-start;
}

.eyebrow {
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #9a4c00;
  margin-bottom: 8px;
}

.memo-hero h1 {
  font-size: clamp(28px, 4vw, 42px);
  line-height: 1.06;
  max-width: 760px;
  margin-bottom: 10px;
}

.hero-copy {
  max-width: 720px;
  color: #5f6b7a;
  font-size: 16px;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.quick-card,
.summary-card,
.memo-list-wrap,
.modal-card {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.08);
  backdrop-filter: blur(10px);
}

.quick-card {
  border-radius: 28px;
  padding: 24px;
}

.quick-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 14px;
}

.quick-header h2 {
  font-size: 24px;
  margin-bottom: 4px;
}

.quick-header p {
  color: #5f6b7a;
}

.cheap-badge {
  padding: 8px 14px;
  border-radius: 999px;
  background: #f6e7c9;
  color: #8c4a00;
  font-size: 13px;
}

.quick-input,
.field,
.search-input {
  width: 100%;
  border: 1px solid #d4dbe5;
  border-radius: 18px;
  padding: 14px 16px;
  background: rgba(255, 255, 255, 0.96);
  color: #1f2937;
  outline: none;
}

.quick-input,
.textarea {
  min-height: 132px;
  resize: vertical;
}

.quick-actions,
.modal-actions,
.memo-actions,
.toolbar-right,
.tabs,
.ops-list {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.quick-actions {
  margin-top: 14px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}

.tab-btn,
.ghost-btn,
.solid-btn,
.danger-btn,
.icon-btn {
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: transform 0.18s ease, opacity 0.18s ease, background 0.18s ease;
}

.tab-btn {
  padding: 10px 16px;
  background: rgba(255, 255, 255, 0.75);
  color: #526172;
}

.tab-btn.active {
  background: #203247;
  color: #fff;
}

.ghost-btn,
.solid-btn,
.danger-btn,
.icon-btn {
  padding: 11px 18px;
}

.compact {
  padding: 8px 14px;
  font-size: 13px;
}

.ghost-btn,
.icon-btn {
  background: rgba(255, 255, 255, 0.9);
  color: #203247;
  border: 1px solid rgba(32, 50, 71, 0.15);
}

.solid-btn {
  background: linear-gradient(135deg, #203247, #315273);
  color: #fff;
}

.danger-btn {
  background: #fff1ef;
  color: #b42318;
  border: 1px solid rgba(180, 35, 24, 0.15);
}

.ghost-btn:hover,
.solid-btn:hover,
.danger-btn:hover,
.icon-btn:hover,
.tab-btn:hover {
  transform: translateY(-1px);
}

.check-wrap {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #526172;
  font-size: 14px;
}

.search-input {
  min-width: 220px;
  border-radius: 999px;
}

.memo-grid {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.summary-card {
  border-radius: 24px;
  padding: 20px;
  position: sticky;
  top: 24px;
}

.summary-card h3 {
  font-size: 18px;
  margin-bottom: 18px;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid rgba(148, 163, 184, 0.18);
}

.summary-row strong {
  font-size: 22px;
}

.memo-list-wrap {
  border-radius: 28px;
  padding: 18px;
}

.search-banner {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(32, 50, 71, 0.96), rgba(49, 82, 115, 0.94));
  color: #fff;
}

.search-banner p {
  margin-top: 4px;
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
}

.memo-list {
  display: grid;
  gap: 14px;
}

.memo-card {
  padding: 18px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(246, 247, 251, 0.92));
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.memo-card.done {
  opacity: 0.88;
}

.memo-card.overdue {
  border-color: rgba(234, 88, 12, 0.28);
}

.memo-top {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.memo-top h3 {
  font-size: 20px;
  margin-bottom: 8px;
}

.meta-line {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.badge {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
}

.badge.todo {
  background: #e8f1ff;
  color: #2457b5;
}

.badge.done {
  background: #e7f7ee;
  color: #137a45;
}

.badge.ai {
  background: #f8ecff;
  color: #8c2ad3;
}

.badge.warn {
  background: #fff2e8;
  color: #c35b0d;
}

.memo-content {
  margin: 14px 0 12px;
  color: #405264;
  line-height: 1.8;
}

.memo-times,
.mini-line {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #66788a;
  margin-bottom: 14px;
}

.empty-state {
  text-align: center;
  color: #66788a;
  padding: 48px 12px;
}

.pager {
  margin-top: 18px;
  display: flex;
  justify-content: center;
  gap: 16px;
  align-items: center;
  color: #526172;
}

.modal-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.44);
  z-index: 300;
}

.modal-card {
  width: min(560px, 100%);
  border-radius: 28px;
  padding: 22px;
}

.wide-card {
  width: min(760px, 100%);
}

.modal-card h3 {
  font-size: 24px;
  margin-bottom: 16px;
}

.modal-card label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 14px;
  color: #526172;
  margin-bottom: 12px;
}

.hint {
  color: #9a4c00;
  background: #f6e7c9;
  border-radius: 16px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.hint.strong {
  font-weight: 600;
}

.query-box,
.op-card {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 18px;
  padding: 14px;
  margin-bottom: 12px;
  background: rgba(255, 255, 255, 0.78);
}

.unresolved-box {
  background: #fff7ed;
}

.query-box ul {
  padding-left: 18px;
}

.query-box li {
  margin: 8px 0;
}

.op-card {
  flex: 1 1 280px;
}

.op-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

@media (max-width: 960px) {
  .memo-page {
    padding: 20px;
  }

  .memo-hero,
  .toolbar,
  .memo-grid {
    flex-direction: column;
    display: flex;
  }

  .memo-grid {
    gap: 14px;
  }

  .summary-card {
    position: static;
  }

  .toolbar-right {
    width: 100%;
  }

  .search-input {
    min-width: 0;
    flex: 1;
  }

  .search-banner {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
