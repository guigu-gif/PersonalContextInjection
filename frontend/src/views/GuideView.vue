<template>
  <main class="guide-page">
    <AppMenu />

    <section class="card">
      <h1>攻略中心</h1>
      <p class="desc">先用上海种子攻略验证“检索 + 引用 + 评分”闭环。</p>
      <div class="filters">
        <input v-model.trim="city" placeholder="城市（默认上海）" />
        <input v-model.trim="keyword" placeholder="关键词（地标/换乘/避坑）" />
        <button class="solid-btn" @click="loadGuides">查询</button>
      </div>
    </section>

    <section class="card">
      <h2>攻略列表</h2>
      <div v-if="!list.length" class="empty">暂无数据</div>
      <article v-for="item in list" :key="item.id" class="guide-item">
        <header>
          <strong>{{ item.title }}</strong>
          <span class="badge" :class="item.isOfficial === 1 ? 'official' : 'ugc'">
            {{ item.isOfficial === 1 ? '官方' : '用户' }}
          </span>
        </header>
        <p class="meta">
          评分 {{ item.score }} · 👍{{ item.likeCount }} · ⭐{{ item.favCount }} · 🪙{{ item.coinCount }} · ⚡{{ item.chargeCount }}
        </p>
        <p class="content">{{ item.content }}</p>
        <div class="actions">
          <button class="ghost-btn" @click="doAction(item.id, 'LIKE')">{{ item.liked ? '取消点赞' : '点赞' }}</button>
          <button class="ghost-btn" @click="doAction(item.id, 'FAV')">{{ item.favored ? '取消收藏' : '收藏' }}</button>
          <button class="ghost-btn" @click="doAction(item.id, 'COIN')">投币</button>
          <button class="ghost-btn" @click="doAction(item.id, 'CHARGE')">充电</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/utils/request'
import AppMenu from '@/components/AppMenu.vue'

type GuideCard = {
  id: number
  title: string
  content: string
  isOfficial: number
  score: number
  likeCount: number
  favCount: number
  coinCount: number
  chargeCount: number
  liked: boolean
  favored: boolean
}

const city = ref('上海')
const keyword = ref('')
const list = ref<GuideCard[]>([])

async function loadGuides() {
  const res: any = await request.get('/guide', {
    params: { city: city.value, keyword: keyword.value, page: 1, size: 50 }
  })
  if (res?.success) list.value = res.data || []
}

async function doAction(guideId: number, actionType: 'LIKE' | 'FAV' | 'COIN' | 'CHARGE') {
  const res: any = await request.post(`/guide/${guideId}/action`, { actionType })
  if (!res?.success || !res?.data) return
  const updated = res.data as GuideCard
  list.value = list.value.map(item => (item.id === guideId ? { ...item, ...updated } : item))
}

onMounted(loadGuides)
</script>

<style scoped>
.guide-page {
  max-width: 960px;
  margin: 24px auto;
  padding: 68px 16px 28px;
  display: grid;
  gap: 14px;
}

.card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.08);
}

.desc {
  color: #64748b;
  margin-top: 6px;
}

.filters {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}

input {
  flex: 1;
  border: 1px solid #d8e0ef;
  border-radius: 10px;
  padding: 9px 12px;
}

.solid-btn,
.ghost-btn {
  border: 0;
  border-radius: 10px;
  padding: 8px 12px;
  cursor: pointer;
}

.solid-btn {
  background: #3b82f6;
  color: #fff;
}

.ghost-btn {
  background: #eef2ff;
  color: #1e293b;
}

.guide-item {
  margin-top: 10px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 12px;
}

.guide-item header {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.badge {
  font-size: 12px;
  border-radius: 999px;
  padding: 2px 8px;
}

.badge.official {
  background: #dbeafe;
  color: #1d4ed8;
}

.badge.ugc {
  background: #f1f5f9;
  color: #475569;
}

.meta {
  margin-top: 6px;
  color: #64748b;
  font-size: 13px;
}

.content {
  margin-top: 8px;
  color: #334155;
  line-height: 1.7;
}

.actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.empty {
  margin-top: 8px;
  color: #64748b;
}
</style>
