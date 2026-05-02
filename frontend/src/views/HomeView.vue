<template>
  <div class="home-shell">
    <AppMenu />

    <main class="main-content">
      <header class="top-bar">
        <div>
          <p class="eyebrow">Personal Context Injection</p>
          <h1>今天有什么新内容，也有什么事情需要尽快处理。</h1>
        </div>
        <div class="top-actions">
          <LiveClock />
          <router-link class="quick-link" to="/memo">+ 新建备忘录</router-link>
        </div>
      </header>

      <section class="hero-grid">
        <article class="hero-card feature-card">
          <span class="chip warm">今日推荐</span>
          <h2>首页先看内容流，常用工具放在左边，需要时再展开。</h2>
          <p>把通知、待办和动态汇总到一个入口，打开应用时先看到重点，而不是一堆按钮。</p>
          <div class="hero-actions">
            <router-link class="solid-link" to="/memo">打开备忘录</router-link>
            <router-link class="ghost-link" to="/schedule">查看课程表</router-link>
          </div>
        </article>

        <article class="hero-card stat-card" :class="unreadCount > 0 ? 'has-data' : ''">
          <span class="chip cool">通知</span>
          <strong>{{ unreadCount }}</strong>
          <p>未读通知</p>
        </article>

        <article class="hero-card stat-card" :class="todoCount > 0 ? 'has-data' : ''">
          <span class="chip dark">待办</span>
          <strong>{{ todoCount }}</strong>
          <p>未完成备忘录</p>
        </article>
      </section>

      <section class="content-grid">
        <div class="feed-column">
          <article v-if="announcementsLoading" class="feed-card">
            <p class="empty-block">加载中...</p>
          </article>
          <article v-for="item in announcements" :key="item.id" class="feed-card">
            <div class="feed-top">
              <span class="chip warm">系统公告</span>
              <span class="feed-time">{{ formatTime(item.createdTime) }}</span>
            </div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.content }}</p>
          </article>
        </div>

        <div class="right-column">
          <article class="side-card notify-card">
            <div class="card-head">
              <h3>最近通知</h3>
              <span v-if="unreadCount > 0" class="unread-dot">{{ unreadCount }} 未读</span>
            </div>
            <div v-if="notifyLoading" class="empty-block">正在加载...</div>
            <div v-else-if="!latestNotifications.length" class="empty-block">
              还没有通知，备忘提醒会显示在这里。
            </div>
            <ul v-else class="notify-list">
              <li v-for="item in latestNotifications" :key="item.id" :class="item.isRead === 0 ? 'unread' : ''">
                <p>{{ item.content }}</p>
                <span>{{ formatTime(item.createdTime) }}</span>
              </li>
            </ul>
          </article>

          <article class="side-card">
            <div class="card-head">
              <h3>今日摘要</h3>
              <span class="mini">系统管理上下文</span>
            </div>
            <ul class="summary-list">
              <li>内容流是默认入口，功能页放进侧边栏。</li>
              <li>AI 和记忆由系统统一管理，不要求用户手动维护。</li>
              <li>先把稳定、便宜、可持续的能力做好，再逐步扩展。</li>
            </ul>
          </article>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import AppMenu from '@/components/AppMenu.vue'
import LiveClock from '@/components/LiveClock.vue'
import request from '@/utils/request'

type NotificationItem = {
  id: number
  content: string
  createdTime: string
  isRead: number
}

type AnnouncementItem = {
  id: number
  title: string
  content: string
  createdTime: string
}

const notifyLoading = ref(false)
const announcementsLoading = ref(false)
const unreadCount = ref(0)
const todoCount = ref(0)
const latestNotifications = ref<NotificationItem[]>([])
const announcements = ref<AnnouncementItem[]>([])

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

async function fetchAnnouncements() {
  announcementsLoading.value = true
  try {
    const res: any = await request.get('/announcement')
    if (res.success) announcements.value = res.data ?? []
  } catch {
    // silent
  } finally {
    announcementsLoading.value = false
  }
}

async function fetchNotifySummary() {
  notifyLoading.value = true
  try {
    const res: any = await request.get('/notify/summary')
    if (res.success) {
      unreadCount.value = res.data?.unread ?? 0
      latestNotifications.value = res.data?.latest ?? []
    }
  } catch {
    // silent
  } finally {
    notifyLoading.value = false
  }
}

async function fetchTodoSummary() {
  try {
    const res: any = await request.get('/memo', { params: { page: 1, size: 1, status: 'todo' } })
    if (res.success) todoCount.value = res.total ?? 0
  } catch {
    // silent
  }
}

onMounted(() => {
  fetchNotifySummary()
  fetchTodoSummary()
  fetchAnnouncements()
})
</script>

<style scoped>
.home-shell {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(244, 119, 71, 0.15), transparent 22%),
    radial-gradient(circle at top right, rgba(60, 132, 206, 0.15), transparent 24%),
    linear-gradient(180deg, #f8f1e8 0%, #f7f7f4 45%, #eef4f7 100%);
  color: #233241;
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

.main-content {
  padding: 72px 28px 48px;
  max-width: 1280px;
  margin: 0 auto;
}

.top-bar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #8f5a21;
  margin-bottom: 8px;
}

.top-bar h1 {
  font-size: clamp(24px, 3.2vw, 38px);
  line-height: 1.1;
  max-width: 680px;
  color: #1e293b;
}

.top-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  flex-shrink: 0;
}

.quick-link {
  display: inline-block;
  padding: 11px 20px;
  border-radius: 999px;
  background: linear-gradient(135deg, #213246, #3f6589);
  color: #fff;
  text-decoration: none;
  font-size: 14px;
  font-weight: 500;
  white-space: nowrap;
  transition: opacity 0.18s, transform 0.18s;
}
.quick-link:hover {
  opacity: 0.88;
  transform: translateY(-1px);
}

.hero-grid {
  display: grid;
  grid-template-columns: 1.6fr 1fr 1fr;
  gap: 16px;
  margin-bottom: 20px;
}

.hero-card,
.feed-card,
.side-card {
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.07);
  padding: 24px;
  backdrop-filter: blur(8px);
}

.feature-card h2 {
  font-size: 26px;
  line-height: 1.1;
  margin: 14px 0 10px;
  color: #1e293b;
}

.feature-card p,
.feed-card p {
  color: #58687a;
  line-height: 1.7;
}

.hero-actions {
  display: flex;
  gap: 10px;
  margin-top: 18px;
  flex-wrap: wrap;
}

.solid-link,
.ghost-link {
  text-decoration: none;
  border-radius: 999px;
  padding: 10px 18px;
  font-size: 14px;
  transition: opacity 0.18s, transform 0.18s;
}
.solid-link:hover,
.ghost-link:hover {
  opacity: 0.85;
  transform: translateY(-1px);
}
.solid-link {
  background: linear-gradient(135deg, #213246, #3f6589);
  color: #fff;
}
.ghost-link {
  background: rgba(35, 50, 65, 0.08);
  color: #233241;
}

.stat-card {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: box-shadow 0.2s;
}
.stat-card.has-data {
  border-color: rgba(79, 70, 229, 0.2);
  box-shadow: 0 18px 50px rgba(79, 70, 229, 0.08);
}
.stat-card strong {
  font-size: 52px;
  line-height: 1;
  margin: 14px 0 8px;
  color: #1e293b;
}
.stat-card p {
  color: #64748b;
  font-size: 14px;
}

.chip {
  display: inline-flex;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
}
.chip.warm { background: #f8dfca; color: #9a4b00; }
.chip.cool { background: #d9edf7; color: #146184; }
.chip.dark { background: #e6e9ed; color: #344152; }

.content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 340px;
  gap: 16px;
}

.feed-column,
.right-column {
  display: grid;
  gap: 16px;
  align-content: start;
}

.feed-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.feed-card h3 {
  font-size: 20px;
  margin-bottom: 8px;
  color: #1e293b;
  line-height: 1.3;
}

.feed-time {
  font-size: 12px;
  color: #94a3b8;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.side-card h3 {
  font-size: 18px;
  color: #1e293b;
}

.mini {
  font-size: 12px;
  color: #94a3b8;
}

.unread-dot {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #fee2e2;
  color: #dc2626;
  font-weight: 500;
}

.notify-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 12px;
}
.notify-list li {
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}
.notify-list li:last-child {
  border-bottom: none;
  padding-bottom: 0;
}
.notify-list li.unread p {
  font-weight: 500;
  color: #1e293b;
}
.notify-list p {
  line-height: 1.6;
  margin-bottom: 4px;
  color: #475569;
  font-size: 14px;
}
.notify-list span {
  font-size: 12px;
  color: #94a3b8;
}

.summary-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 10px;
}
.summary-list li {
  font-size: 14px;
  line-height: 1.7;
  color: #58687a;
  padding-left: 14px;
  position: relative;
}
.summary-list li::before {
  content: '·';
  position: absolute;
  left: 0;
  color: #94a3b8;
}

.empty-block {
  color: #94a3b8;
  font-size: 14px;
  line-height: 1.8;
}

@media (max-width: 1024px) {
  .hero-grid {
    grid-template-columns: 1fr 1fr;
  }
  .feature-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .main-content {
    padding: 72px 16px 32px;
  }
  .top-bar {
    flex-direction: column;
    align-items: flex-start;
  }
  .hero-grid,
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
