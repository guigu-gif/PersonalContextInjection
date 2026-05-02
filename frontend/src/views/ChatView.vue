<template>
  <div class="chat-page">
    <AppMenu />

    <div class="chat-layout">
      <!-- 左侧上下文面板 -->
      <aside class="context-panel">
        <div class="ctx-header">
          <p class="eyebrow">当前注入上下文</p>
          <h2>今日快照</h2>
        </div>
        <div v-if="ctxLoading" class="ctx-loading">加载中...</div>
        <template v-else-if="context">
          <div class="ctx-block">
            <p class="ctx-label">{{ context.weekday }} · {{ context.date }}</p>
          </div>
          <div class="ctx-block">
            <p class="ctx-title">今日课程</p>
            <div v-if="!context.todayCourses?.length" class="ctx-empty">今天没有课</div>
            <div v-else class="ctx-list">
              <div v-for="c in context.todayCourses" :key="c.id" class="ctx-item">
                <span class="ctx-dot course-dot" />
                <div>
                  <p>{{ c.name }}</p>
                  <p class="ctx-sub">第{{ c.startSlot }}-{{ c.endSlot }}节{{ c.location ? ' · ' + c.location : '' }}</p>
                </div>
              </div>
            </div>
          </div>
          <div class="ctx-block">
            <p class="ctx-title">待办备忘录</p>
            <div v-if="!context.pendingMemos?.length" class="ctx-empty">暂无待办</div>
            <div v-else class="ctx-list">
              <div v-for="m in context.pendingMemos" :key="m.id" class="ctx-item">
                <span class="ctx-dot memo-dot" />
                <div>
                  <p>{{ m.title }}</p>
                  <p v-if="m.remindTime" class="ctx-sub">{{ formatTime(m.remindTime) }}</p>
                </div>
              </div>
            </div>
          </div>
          <div class="ctx-block">
            <p class="ctx-title">未读通知</p>
            <p class="ctx-count">{{ context.unreadCount }} 条</p>
          </div>
        </template>
      </aside>

      <!-- 右侧对话区 -->
      <main class="chat-main">
        <div class="chat-header">
          <div>
            <p class="eyebrow">Personal Context AI</p>
            <h1>跨功能智能助手</h1>
          </div>
          <button class="ghost-btn" @click="clearHistory">清空对话</button>
        </div>

        <div ref="msgListRef" class="msg-list">
          <div v-if="!messages.length" class="welcome-block">
            <p class="welcome-title">你好，有什么可以帮你？</p>
            <div class="suggestions">
              <button v-for="s in suggestions" :key="s" class="suggest-btn" @click="sendSuggestion(s)">
                {{ s }}
              </button>
            </div>
          </div>

          <div v-for="(msg, i) in messages" :key="i" :class="['msg-row', msg.role]">
            <div class="msg-bubble">
              <p>{{ msg.content }}</p>
              <div v-if="msg.role === 'assistant' && msg.citations?.length" class="citation-block">
                <p class="citation-title">引用来源</p>
                <ul>
                  <li v-for="cite in msg.citations" :key="`${i}-${cite.guideId}`">
                    [来源ID={{ cite.guideId }}][{{ cite.sourceType }}][评分={{ cite.score }}] {{ cite.title }}
                  </li>
                </ul>
              </div>
            </div>
          </div>

          <div v-if="loading" class="msg-row assistant">
            <div class="msg-bubble typing">
              <span /><span /><span />
            </div>
          </div>

          <div v-if="pendingJump" class="msg-row assistant">
            <div class="msg-bubble travel-link-card">
              <p>
                {{ pendingJump.message }}
                <a class="jump-link" href="javascript:void(0)" @click="goWithPendingJump">点这里跳转</a>
                <a class="jump-link cancel" href="javascript:void(0)" @click="pendingJump = null">忽略</a>
              </p>
            </div>
          </div>
        </div>

        <div class="input-bar">
          <input ref="imageInputRef" class="hidden-input" type="file" accept="image/*" @change="onImageSelected" />
          <button class="attach-btn" :disabled="loading" @click="openImagePicker">📷</button>
          <textarea
            v-model="input"
            class="chat-input"
            :placeholder="imageName ? `已选图片：${imageName}` : '问我今天有什么课、还有什么待办、或者任何问题...'"
            rows="1"
            @keydown.enter.exact.prevent="send"
            @input="autoResize"
          />
          <button class="send-btn" :disabled="loading || (!input.trim() && !imageBase64)" @click="send">发送</button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppMenu from '@/components/AppMenu.vue'
import request from '@/utils/request'

type Citation = { guideId: number; sourceType: string; score: number; title: string }
type Message = { role: 'user' | 'assistant'; content: string; citations?: Citation[] }
type Context = {
  date: string
  weekday: string
  unreadCount: number
  todayCourses: any[]
  pendingMemos: any[]
}

const messages = ref<Message[]>([])
const input = ref('')
const loading = ref(false)
const ctxLoading = ref(false)
const context = ref<Context | null>(null)
const msgListRef = ref<HTMLElement | null>(null)
const imageBase64 = ref('')
const imageName = ref('')
const imageInputRef = ref<HTMLInputElement | null>(null)
const pendingJump = ref<{ path: string; query?: Record<string, string>; message: string } | null>(null)
const router = useRouter()

const suggestions = [
  '今天有什么课？',
  '我还有哪些待办事项？',
  '有几条未读通知？',
  '帮我总结一下今天的安排',
]

function formatTime(value?: string) {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

function autoResize(e: Event) {
  const el = e.target as HTMLTextAreaElement
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

async function scrollBottom() {
  await nextTick()
  if (msgListRef.value) {
    msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  }
}

async function send() {
  const text = input.value.trim()
  if ((!text && !imageBase64.value) || loading.value) return
  input.value = ''
  const el = document.querySelector('.chat-input') as HTMLTextAreaElement
  if (el) el.style.height = 'auto'

  messages.value.push({
    role: 'user',
    content: imageBase64.value ? `${text || '请解析这张图片'} [附图: ${imageName.value || 'image'}]` : text
  })
  await scrollBottom()
  loading.value = true

  if (imageBase64.value) {
    await parseImageAndRoute(text)
    return
  }

  // 文本先尝试走“出行意图 -> 路线预览 -> 手动跳转”
  const handledByTravel = await parseTextTravelAndSuggest(text)
  if (handledByTravel) {
    return
  }

  try {
    const history = messages.value.slice(0, -1).map(m => ({ role: m.role, content: m.content }))
    const res: any = await request.post('/chat', { message: text, history })
    if (res.success) {
      const data = res.data
      if (typeof data === 'string') {
        messages.value.push({ role: 'assistant', content: data })
      } else {
        messages.value.push({
          role: 'assistant',
          content: data?.reply || 'AI 暂时无法响应，请稍后重试。',
          citations: Array.isArray(data?.citations) ? data.citations : []
        })
      }
    } else {
      messages.value.push({ role: 'assistant', content: 'AI 暂时无法响应，请稍后重试。' })
    }
  } catch {
    messages.value.push({ role: 'assistant', content: '网络异常，请稍后重试。' })
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

function isLikelyTravelText(text: string) {
  const t = (text || '').trim()
  if (!t) return false
  return /(去|到|怎么走|路线|路程|地铁|公交|打车|导航|出发|目的地|从.+到.+)/.test(t)
}

async function parseTextTravelAndSuggest(text: string) {
  if (!isLikelyTravelText(text)) return false
  pendingJump.value = null
  try {
    const res: any = await request.post('/travel/ai-parse', {
      instruction: text
    })
    if (!res?.success) {
      return false
    }
    const data = res.data || {}
    const draft = data?.draft || {}
    const city = String(draft.city || '').trim()
    const origin = String(draft.origin || '').trim()
    const destination = String(draft.destination || '').trim()
    if (!city || !origin || !destination) {
      messages.value.push({
        role: 'assistant',
        content: data?.unresolved?.[0] || '我识别到你在问出行，但信息还不完整，请补充城市/出发地/目的地。'
      })
      loading.value = false
      await scrollBottom()
      return true
    }
    const routes = Array.isArray(data?.preview?.routes) ? data.preview.routes.length : 0
    pendingJump.value = {
      path: '/travel',
      query: { city, origin, destination, from: 'chat-text' },
      message: `已根据你的文字解析出路线草稿：${city} · ${origin} → ${destination}（预览${routes}条方案），点链接可继续确认执行。`
    }
    messages.value.push({ role: 'assistant', content: pendingJump.value.message })
    loading.value = false
    await scrollBottom()
    return true
  } catch {
    return false
  }
}

async function parseImageAndRoute(text: string) {
  pendingJump.value = null
  try {
    const res: any = await request.post('/chat/image-route', {
      instruction: text || '请识别图片中的出行目的地并规划路线',
      imageBase64: imageBase64.value,
      imageMimeType: inferImageMime(imageName.value)
    })
    if (!res?.success) {
      messages.value.push({ role: 'assistant', content: res?.errorMsg || '图片解析失败，请重试。' })
      return
    }
    const data = res.data || {}
    const jump = buildJumpTarget(data)
    if (jump) {
      pendingJump.value = jump
      messages.value.push({ role: 'assistant', content: jump.message })
    } else {
      messages.value.push({
        role: 'assistant',
        content: data?.reason || '未识别到明确模块，本次不自动跳转。'
      })
    }
  } catch {
    messages.value.push({ role: 'assistant', content: '网络异常，图片解析失败。' })
  } finally {
    loading.value = false
    clearImage()
    await scrollBottom()
  }
}

function sendSuggestion(text: string) {
  input.value = text
  send()
}

function clearHistory() {
  messages.value = []
  pendingJump.value = null
}

function openImagePicker() {
  imageInputRef.value?.click()
}

function onImageSelected(e: Event) {
  const inputEl = e.target as HTMLInputElement
  const file = inputEl.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    messages.value.push({ role: 'assistant', content: '仅支持图片文件（jpg/png/webp）' })
    inputEl.value = ''
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    messages.value.push({ role: 'assistant', content: '图片不能超过4MB' })
    inputEl.value = ''
    return
  }

  imageName.value = file.name
  const reader = new FileReader()
  reader.onload = () => {
    const full = String(reader.result || '')
    const idx = full.indexOf('base64,')
    imageBase64.value = idx >= 0 ? full.substring(idx + 7) : full
  }
  reader.readAsDataURL(file)
}

function clearImage() {
  imageBase64.value = ''
  imageName.value = ''
  if (imageInputRef.value) imageInputRef.value.value = ''
}

function inferImageMime(name: string) {
  const lower = (name || '').toLowerCase()
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.webp')) return 'image/webp'
  return 'image/jpeg'
}

function goWithPendingJump() {
  if (!pendingJump.value) return
  const target = pendingJump.value
  router.push({
    path: target.path,
    query: target.query || {}
  })
  pendingJump.value = null
}

function buildJumpTarget(data: any): { path: string; query?: Record<string, string>; message: string } | null {
  const module = String(data?.module || 'none').toLowerCase()
  if (!data?.shouldJump || module === 'none') return null
  if (module === 'travel') {
    const city = String(data?.draft?.city || '').trim()
    const origin = String(data?.draft?.origin || '').trim()
    const destination = String(data?.draft?.destination || '').trim()
    if (city && origin && destination) {
      return {
        path: '/travel',
        query: { city, origin, destination, from: 'chat-image' },
        message: `已识别出行意图：${city} · ${origin} → ${destination}，可跳转到出行规划继续确认。`
      }
    }
    return {
      path: '/travel',
      message: '识别到出行相关内容，可跳转到出行规划继续处理。'
    }
  }
  const pathMap: Record<string, string> = {
    schedule: '/schedule',
    memo: '/memo',
    notify: '/notify',
    guide: '/guide',
    settings: '/settings'
  }
  const path = pathMap[module]
  if (!path) return null
  return {
    path,
    message: `识别到内容更适合在「${moduleLabel(module)}」处理，可一键跳转。`
  }
}

function moduleLabel(module: string) {
  const labels: Record<string, string> = {
    travel: '出行规划',
    schedule: '课程表',
    memo: '备忘录',
    notify: '通知中心',
    guide: '攻略中心',
    settings: '设置'
  }
  return labels[module] || '对应板块'
}

async function loadContext() {
  ctxLoading.value = true
  try {
    const res: any = await request.get('/chat/context')
    if (res.success) context.value = res.data
  } finally {
    ctxLoading.value = false
  }
}

onMounted(() => loadContext())
</script>

<style scoped>
.chat-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(79, 70, 229, 0.1), transparent 30%),
    radial-gradient(circle at bottom right, rgba(8, 145, 178, 0.08), transparent 30%),
    linear-gradient(160deg, #f0f4ff 0%, #f7f8fc 50%, #eef6f8 100%);
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

.chat-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  height: 100vh;
  padding-top: 0;
}

/* 左侧上下文面板 */
.context-panel {
  padding: 80px 20px 24px;
  border-right: 1px solid rgba(148, 163, 184, 0.16);
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(10px);
}

.ctx-header {
  margin-bottom: 24px;
}

.eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6366f1;
  margin-bottom: 4px;
}

.ctx-header h2 {
  font-size: 20px;
  color: #1e293b;
}

.ctx-loading {
  color: #94a3b8;
  font-size: 14px;
}

.ctx-block {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
}
.ctx-block:last-child {
  border-bottom: none;
}

.ctx-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.ctx-title {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 10px;
}

.ctx-empty {
  font-size: 13px;
  color: #94a3b8;
}

.ctx-list {
  display: grid;
  gap: 10px;
}

.ctx-item {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.ctx-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  margin-top: 5px;
}
.course-dot { background: #4f46e5; }
.memo-dot   { background: #0891b2; }

.ctx-item p {
  font-size: 13px;
  color: #1e293b;
  line-height: 1.5;
}
.ctx-sub {
  color: #94a3b8 !important;
  font-size: 12px !important;
}

.ctx-count {
  font-size: 28px;
  font-weight: 700;
  color: #1e293b;
}

/* 右侧对话区 */
.chat-main {
  display: flex;
  flex-direction: column;
  height: 100vh;
  padding-top: 64px;
}

.chat-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 16px 28px 12px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  flex-shrink: 0;
}

.chat-header h1 {
  font-size: 22px;
  color: #1e293b;
}

.ghost-btn {
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid rgba(32, 50, 71, 0.14);
  background: rgba(255, 255, 255, 0.9);
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
}
.ghost-btn:hover { background: #f1f5f9; }

.msg-list {
  flex: 1;
  overflow-y: auto;
  padding: 24px 28px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.welcome-block {
  margin: auto;
  text-align: center;
  max-width: 480px;
}

.welcome-title {
  font-size: 22px;
  color: #1e293b;
  margin-bottom: 20px;
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.suggest-btn {
  padding: 10px 16px;
  border-radius: 999px;
  border: 1px solid rgba(99, 102, 241, 0.22);
  background: rgba(240, 244, 255, 0.9);
  color: #4f46e5;
  font-size: 14px;
  cursor: pointer;
  transition: background 0.15s, transform 0.15s;
}
.suggest-btn:hover {
  background: #e0e7ff;
  transform: translateY(-1px);
}

.msg-row {
  display: flex;
}
.msg-row.user {
  justify-content: flex-end;
}
.msg-row.assistant {
  justify-content: flex-start;
}

.msg-bubble {
  max-width: 68%;
  padding: 12px 16px;
  border-radius: 18px;
  font-size: 15px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.msg-row.user .msg-bubble {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.msg-row.assistant .msg-bubble {
  background: rgba(255, 255, 255, 0.9);
  color: #1e293b;
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-bottom-left-radius: 6px;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.06);
}

.citation-block {
  margin-top: 10px;
  border-top: 1px dashed rgba(148, 163, 184, 0.35);
  padding-top: 8px;
}

.citation-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.citation-block ul {
  margin: 0;
  padding-left: 16px;
}

.citation-block li {
  font-size: 12px;
  color: #334155;
  line-height: 1.6;
}

.msg-bubble.typing {
  display: flex;
  gap: 5px;
  align-items: center;
  padding: 14px 18px;
}
.msg-bubble.typing span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.2s infinite;
}
.msg-bubble.typing span:nth-child(2) { animation-delay: 0.2s; }
.msg-bubble.typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-6px); }
}

.input-bar {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 16px 28px 24px;
  border-top: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  flex-shrink: 0;
}

.hidden-input {
  display: none;
}

.attach-btn {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: #fff;
  cursor: pointer;
  flex-shrink: 0;
}

.chat-input {
  flex: 1;
  padding: 13px 16px;
  border: 1.5px solid #e2e8f0;
  border-radius: 18px;
  font-size: 15px;
  outline: none;
  resize: none;
  background: rgba(255, 255, 255, 0.95);
  color: #1e293b;
  line-height: 1.6;
  transition: border-color 0.18s;
  overflow-y: hidden;
}
.chat-input:focus { border-color: #6366f1; }

.send-btn {
  padding: 13px 22px;
  border-radius: 18px;
  border: none;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.25);
  transition: opacity 0.15s, transform 0.15s;
}
.send-btn:hover:not(:disabled) {
  opacity: 0.88;
  transform: translateY(-1px);
}
.send-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.travel-link-card {
  border: 1px solid rgba(14, 116, 144, 0.24) !important;
  background: #ecfeff !important;
}

.travel-card-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.travel-btn {
  padding: 6px 12px;
  font-size: 12px;
}

.jump-link {
  color: #2563eb;
  text-decoration: underline;
  margin-left: 8px;
  cursor: pointer;
  font-weight: 500;
}
.jump-link:hover { color: #1d4ed8; }
.jump-link.cancel {
  color: #6b7280;
  font-weight: normal;
}

@media (max-width: 768px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
  .context-panel {
    display: none;
  }
}
</style>
