<template>
  <div class="notify-page">
    <AppMenu />

    <header class="notify-hero">
      <div>
        <p class="eyebrow">Notifications</p>
        <h1>通知中心</h1>
        <p class="hero-sub">备忘提醒和系统消息都在这里。</p>
      </div>
      <button v-if="unreadCount > 0" class="ghost-btn" @click="markAllRead">全部标为已读</button>
    </header>

    <section class="notify-wrap">
      <div v-if="loading" class="empty-state">正在加载...</div>
      <div v-else-if="!list.length" class="empty-state">暂无通知</div>
      <div v-else class="notify-list">
        <article
          v-for="item in list"
          :key="item.id"
          :class="['notify-card', item.isRead === 0 ? 'unread' : '']"
        >
          <div class="notify-left">
            <span :class="['type-dot', item.type === 1 ? 'course' : 'memo']" />
            <div>
              <p class="notify-content">{{ item.content }}</p>
              <span class="notify-time">{{ formatTime(item.createdTime) }}</span>
            </div>
          </div>
          <button v-if="item.isRead === 0" class="read-btn" @click="markRead(item)">标为已读</button>
        </article>
      </div>

      <div v-if="totalPages > 1" class="pager">
        <button class="ghost-btn compact" :disabled="page <= 1" @click="fetchList(page - 1)">上一页</button>
        <span>第 {{ page }} / {{ totalPages }} 页</span>
        <button class="ghost-btn compact" :disabled="page >= totalPages" @click="fetchList(page + 1)">下一页</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppMenu from '@/components/AppMenu.vue'
import request from '@/utils/request'

type NotifyItem = {
  id: number
  type: number
  content: string
  isRead: number
  createdTime: string
}

const list = ref<NotifyItem[]>([])
const loading = ref(false)
const page = ref(1)
const pageSize = 15
const total = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const unreadCount = computed(() => list.value.filter(n => n.isRead === 0).length)

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

async function fetchList(targetPage = 1) {
  loading.value = true
  try {
    page.value = targetPage
    const res: any = await request.get('/notify', { params: { page: page.value, size: pageSize } })
    if (res.success) {
      list.value = res.data ?? []
      total.value = res.total ?? 0
    }
  } finally {
    loading.value = false
  }
}

async function markRead(item: NotifyItem) {
  const res: any = await request.put(`/notify/${item.id}/read`)
  if (res.success) item.isRead = 1
}

async function markAllRead() {
  const unread = list.value.filter(n => n.isRead === 0)
  await Promise.all(unread.map(n => request.put(`/notify/${n.id}/read`)))
  unread.forEach(n => { n.isRead = 1 })
}

onMounted(() => fetchList(1))
</script>

<style scoped>
.notify-page {
  min-height: 100vh;
  padding: 72px 32px 40px;
  background:
    radial-gradient(circle at top left, rgba(8, 145, 178, 0.12), transparent 28%),
    radial-gradient(circle at top right, rgba(79, 70, 229, 0.1), transparent 25%),
    linear-gradient(180deg, #eef6f8 0%, #f5f7fb 50%, #f0f4ff 100%);
  color: #1f2937;
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

.notify-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  width: min(860px, 100%);
  margin: 0 auto 24px;
  gap: 16px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #146184;
  margin-bottom: 6px;
}

.notify-hero h1 {
  font-size: clamp(28px, 4vw, 40px);
  line-height: 1.08;
  margin-bottom: 6px;
}

.hero-sub {
  color: #5f6b7a;
  font-size: 15px;
}

.notify-wrap {
  width: min(860px, 100%);
  margin: 0 auto;
}

.notify-list {
  display: grid;
  gap: 10px;
}

.notify-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.05);
  backdrop-filter: blur(8px);
  transition: box-shadow 0.18s;
}

.notify-card.unread {
  border-color: rgba(79, 70, 229, 0.22);
  background: rgba(240, 244, 255, 0.9);
}

.notify-left {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  flex: 1;
  min-width: 0;
}

.type-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 5px;
}
.type-dot.course { background: #4f46e5; }
.type-dot.memo   { background: #0891b2; }

.notify-content {
  font-size: 15px;
  color: #1e293b;
  line-height: 1.6;
  margin-bottom: 4px;
}

.notify-time {
  font-size: 12px;
  color: #94a3b8;
}

.read-btn {
  flex-shrink: 0;
  padding: 7px 14px;
  border-radius: 999px;
  border: 1px solid rgba(79, 70, 229, 0.2);
  background: rgba(240, 244, 255, 0.9);
  color: #4f46e5;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.read-btn:hover { background: #e0e7ff; }

.ghost-btn {
  padding: 10px 18px;
  border-radius: 999px;
  border: 1px solid rgba(32, 50, 71, 0.15);
  background: rgba(255, 255, 255, 0.9);
  color: #203247;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.ghost-btn:hover:not(:disabled) { background: #f1f5f9; transform: translateY(-1px); }
.ghost-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ghost-btn.compact { padding: 8px 14px; font-size: 13px; }

.empty-state {
  text-align: center;
  color: #94a3b8;
  padding: 64px 12px;
  font-size: 15px;
}

.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  color: #64748b;
  font-size: 14px;
}
</style>
