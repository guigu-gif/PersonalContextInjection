<template>
  <div class="float-ai" :class="{ open: isOpen }" :style="floatStyle">
    <!-- 气泡按钮 -->
    <button
      v-if="!isOpen"
      class="float-trigger"
      title="AI 助手"
      @pointerdown="onTriggerPointerDown"
      @click="onTriggerClick"
    >
      ✦
    </button>

    <!-- 小窗 -->
    <div v-if="isOpen" class="float-panel">
      <div class="fp-header">
        <span>✦ AI 助手</span>
        <div class="fp-actions">
          <router-link to="/chat" class="fp-expand" title="展开完整对话" @click="isOpen = false">⤢</router-link>
          <button class="fp-close" @click="isOpen = false">✕</button>
        </div>
      </div>

      <div ref="msgRef" class="fp-msgs">
        <div v-if="!messages.length" class="fp-welcome">
          <p>有什么可以帮你？</p>
          <div class="fp-suggests">
            <button v-for="s in suggests" :key="s" @click="send(s)">{{ s }}</button>
          </div>
        </div>
        <div v-for="(m, i) in messages" :key="i" :class="['fp-msg', m.role]">
          <p>{{ m.content }}</p>
        </div>
        <div v-if="loading" class="fp-msg assistant">
          <p class="typing"><span /><span /><span /></p>
        </div>

        <!-- 设置确认卡片 -->
        <div v-if="pendingAction" class="fp-confirm-card">
          <p>{{ pendingAction.label }}，确认执行？</p>
          <div class="fp-confirm-btns">
            <button class="fp-btn-ok" @click="confirmAction">确认</button>
            <button class="fp-btn-cancel" @click="cancelAction">取消</button>
          </div>
        </div>

        <!-- 画像记录确认卡片 -->
        <div v-if="pendingPersona" class="fp-confirm-card persona-card">
          <p>记录到画像：<strong>{{ pendingPersona.factKey }}</strong> → {{ pendingPersona.factValue }}</p>
          <div class="fp-confirm-btns">
            <button class="fp-btn-ok" @click="confirmPersona">确认记录</button>
            <button class="fp-btn-cancel" @click="cancelPersona">不记录</button>
          </div>
        </div>

        <!-- 模块跳转卡片：链接式手动跳转 -->
        <div v-if="pendingJump" class="fp-confirm-card travel-card">
          <p>
            {{ pendingJump.message }}
            <a class="jump-link" href="javascript:void(0)" @click="goWithPendingJump">点这里跳转</a>
            <a class="jump-link cancel" href="javascript:void(0)" @click="pendingJump = null">忽略</a>
          </p>
        </div>
      </div>

      <div class="fp-input-row">
        <input ref="imageInputRef" class="hidden-input" type="file" accept="image/*" @change="onImageSelected" />
        <button class="fp-attach" :disabled="loading" @click="openImagePicker">📷</button>
        <input
          v-model="input"
          class="fp-input"
          :placeholder="imageName ? `已选图片：${imageName}` : '问一下...'"
          @keydown.enter="send()"
        />
        <button class="fp-send" :disabled="loading || (!input.trim() && !imageBase64)" @click="send()">→</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import request from '@/utils/request'
import { useProfileStore } from '@/stores/profile'
import { useRouter } from 'vue-router'

type Msg = { role: 'user' | 'assistant'; content: string }
type PendingAction = { action: string; value: string; label: string }
type PendingPersona = { factKey: string; factValue: string; label: string }
type PendingJump = { path: string; query?: Record<string, string>; message: string }

const isOpen = ref(false)
const input = ref('')
const loading = ref(false)
const messages = ref<Msg[]>([])
const msgRef = ref<HTMLElement | null>(null)
const pendingAction = ref<PendingAction | null>(null)
const pendingPersona = ref<PendingPersona | null>(null)
const pendingJump = ref<PendingJump | null>(null)
const imageBase64 = ref('')
const imageName = ref('')
const imageInputRef = ref<HTMLInputElement | null>(null)
const posX = ref(0)
const posY = ref(0)
const dragging = ref(false)
const dragged = ref(false)
const dragStartX = ref(0)
const dragStartY = ref(0)
const startPosX = ref(0)
const startPosY = ref(0)

const profileStore = useProfileStore()
const router = useRouter()
const suggests = ['今天有什么课？', '还有什么待办？']
const POS_KEY = 'float_ai_pos_v1'

const floatStyle = computed(() => ({
  left: `${posX.value}px`,
  top: `${posY.value}px`
}))

// 本地意图识别：匹配设置类操作，不走 AI
const SETTING_PATTERNS: { pattern: RegExp; action: string; value: string; label: string }[] = [
  { pattern: /字.*大|大.*字|看不清|字体.*大/, action: 'SET_FONT_SIZE', value: 'large',   label: '字体调为大号' },
  { pattern: /字.*超大|最大|老年.*字/,         action: 'SET_FONT_SIZE', value: 'xlarge',  label: '字体调为超大号' },
  { pattern: /字.*正常|恢复.*字/,              action: 'SET_FONT_SIZE', value: 'normal',  label: '字体恢复正常' },
  { pattern: /老年.*模式|老人.*模式|长辈/,      action: 'SET_THEME',     value: 'elder',   label: '切换为老年模式' },
  { pattern: /暗.*色|深色|夜间/,               action: 'SET_THEME',     value: 'dark',    label: '切换为暗色模式' },
  { pattern: /默认.*主题|恢复.*主题|正常.*主题/, action: 'SET_THEME',     value: 'default', label: '恢复默认主题' },
  { pattern: /我是学生|学生身份/,               action: 'SET_IDENTITY',  value: 'student', label: '设置身份为学生' },
  { pattern: /我是老人|老年人|长辈身份/,         action: 'SET_IDENTITY',  value: 'elder',   label: '设置身份为老年人' },
]

function detectSettingIntent(text: string): PendingAction | null {
  for (const p of SETTING_PATTERNS) {
    if (p.pattern.test(text)) {
      return { action: p.action, value: p.value, label: p.label }
    }
  }
  return null
}

async function scrollBottom() {
  await nextTick()
  if (msgRef.value) msgRef.value.scrollTop = msgRef.value.scrollHeight
}

async function send(text?: string) {
  const msg = (text ?? input.value).trim()
  if ((!msg && !imageBase64.value) || loading.value) return
  input.value = ''
  messages.value.push({
    role: 'user',
    content: imageBase64.value ? `${msg || '请解析这张图片'} [附图: ${imageName.value || 'image'}]` : msg
  })
  await scrollBottom()

  // 图片优先走多模块路由（解析->按模块跳转）
  if (imageBase64.value) {
    await parseImageAndRoute(msg)
    return
  }

  // 文本优先尝试“出行问询 -> 方案预览 -> 手动跳转”
  const handledByTravel = await parseTextTravelAndSuggest(msg)
  if (handledByTravel) {
    return
  }

  // 先检查是否是设置意图
  const intent = detectSettingIntent(msg)
  if (intent) {
    pendingAction.value = intent
    messages.value.push({ role: 'assistant', content: `我帮你${intent.label}，确认吗？` })
    await scrollBottom()
    return
  }

  // 检查是否是画像记录意图
  const personaIntent = detectPersonaIntent(msg)
  if (personaIntent) {
    pendingPersona.value = personaIntent
    messages.value.push({ role: 'assistant', content: `我把「${personaIntent.factValue}」记录到你的画像里，确认吗？` })
    await scrollBottom()
    return
  }

  loading.value = true
  try {
    const history = messages.value.slice(0, -1).map(m => ({ role: m.role, content: m.content }))
    const res: any = await request.post('/chat', { message: msg, history })
    if (!res.success) {
      messages.value.push({ role: 'assistant', content: 'AI 暂时无法响应' })
    } else if (typeof res.data === 'string') {
      messages.value.push({ role: 'assistant', content: res.data })
    } else {
      messages.value.push({ role: 'assistant', content: res.data?.reply || 'AI 暂时无法响应' })
    }
  } catch {
    messages.value.push({ role: 'assistant', content: '网络异常，请稍后重试' })
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

async function parseTextTravelAndSuggest(msg: string) {
  if (!isLikelyTravelText(msg)) return false
  pendingJump.value = null
  loading.value = true
  try {
    const res: any = await request.post('/travel/ai-parse', {
      instruction: msg
    })
    if (!res?.success) return false

    const data = res.data || {}
    const draft = data?.draft || {}
    const city = String(draft.city || '').trim()
    const origin = String(draft.origin || '').trim()
    const destination = String(draft.destination || '').trim()
    if (!city || !origin || !destination) {
      messages.value.push({
        role: 'assistant',
        content: data?.unresolved?.[0] || '我识别到你在问出行，但信息不完整，请补充城市/出发地/目的地。'
      })
      return true
    }

    const routes = Array.isArray(data?.preview?.routes) ? data.preview.routes.length : 0
    pendingJump.value = {
      path: '/travel',
      query: { city, origin, destination, from: 'float-ai-text' },
      message: `已解析出行方案：${city} · ${origin} → ${destination}（预览${routes}条），点链接跳转继续确认。`
    }
    messages.value.push({ role: 'assistant', content: pendingJump.value.message })
    return true
  } catch {
    return false
  } finally {
    loading.value = false
    await scrollBottom()
  }
}

async function parseImageAndRoute(msg: string) {
  loading.value = true
  pendingJump.value = null
  try {
    const res: any = await request.post('/chat/image-route', {
      instruction: msg || '请识别图片内容并判断应跳转的功能板块',
      imageBase64: imageBase64.value,
      imageMimeType: inferImageMime(imageName.value)
    })
    if (!res?.success) {
      messages.value.push({ role: 'assistant', content: res?.errorMsg || '图片解析失败' })
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
        content: data?.reason || '未识别到明确板块，本次不自动跳转。'
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
  isOpen.value = false
  pendingJump.value = null
}

function buildJumpTarget(data: any): PendingJump | null {
  const module = String(data?.module || 'none').toLowerCase()
  if (!data?.shouldJump || module === 'none') return null
  if (module === 'travel') {
    const city = String(data?.draft?.city || '').trim()
    const origin = String(data?.draft?.origin || '').trim()
    const destination = String(data?.draft?.destination || '').trim()
    if (city && origin && destination) {
      return {
        path: '/travel',
        query: { city, origin, destination, from: 'float-ai-image' },
        message: `识别到出行：${city} · ${origin} → ${destination}，可跳转继续确认。`
      }
    }
    return { path: '/travel', message: '识别到出行相关内容，可跳转到出行规划继续处理。' }
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
  return { path, message: `识别到内容更适合在「${moduleLabel(module)}」处理，可一键跳转。` }
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

function onTriggerPointerDown(e: PointerEvent) {
  dragging.value = true
  dragged.value = false
  dragStartX.value = e.clientX
  dragStartY.value = e.clientY
  startPosX.value = posX.value
  startPosY.value = posY.value
  window.addEventListener('pointermove', onPointerMove)
  window.addEventListener('pointerup', onPointerUp)
}

function onPointerMove(e: PointerEvent) {
  if (!dragging.value) return
  const dx = e.clientX - dragStartX.value
  const dy = e.clientY - dragStartY.value
  if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
    dragged.value = true
  }
  posX.value = clampX(startPosX.value + dx)
  posY.value = clampY(startPosY.value + dy)
}

function onPointerUp() {
  dragging.value = false
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
  persistPosition()
}

function onTriggerClick() {
  if (dragged.value) return
  isOpen.value = true
}

function clampX(x: number) {
  const min = 8
  const max = window.innerWidth - 60
  return Math.max(min, Math.min(max, x))
}

function clampY(y: number) {
  const min = 8
  const max = window.innerHeight - 60
  return Math.max(min, Math.min(max, y))
}

function persistPosition() {
  localStorage.setItem(POS_KEY, JSON.stringify({ x: posX.value, y: posY.value }))
}

function restorePosition() {
  const raw = localStorage.getItem(POS_KEY)
  if (!raw) {
    posX.value = window.innerWidth - 80
    posY.value = window.innerHeight - 90
    return
  }
  try {
    const pos = JSON.parse(raw)
    posX.value = clampX(Number(pos.x) || window.innerWidth - 80)
    posY.value = clampY(Number(pos.y) || window.innerHeight - 90)
  } catch {
    posX.value = window.innerWidth - 80
    posY.value = window.innerHeight - 90
  }
}

function handleResize() {
  posX.value = clampX(posX.value)
  posY.value = clampY(posY.value)
  persistPosition()
}

async function confirmAction() {
  if (!pendingAction.value) return
  const { action, value, label } = pendingAction.value
  pendingAction.value = null
  const res = await profileStore.applyAction(action, value)
  messages.value.push({
    role: 'assistant',
    content: res.success ? `✓ 已${label}` : '操作失败，请稍后重试'
  })
  await scrollBottom()
}

function cancelAction() {
  pendingAction.value = null
  messages.value.push({ role: 'assistant', content: '好的，已取消。' })
}

const PERSONA_PATTERNS: { pattern: RegExp; factKey: string; extract: (m: RegExpMatchArray) => string }[] = [
  { pattern: /记住我是(.+)|我是(.+)学生|我是(.+)专业/, factKey: '身份', extract: m => (m[1] || m[2] || m[3] || '').trim() },
  { pattern: /我喜欢(.+回答|.+风格)|我偏好(.+)/, factKey: '偏好', extract: m => (m[1] || m[2] || '').trim() },
  { pattern: /我关注(.+)|我在学(.+)/, factKey: '关注', extract: m => (m[1] || m[2] || '').trim() },
]

function detectPersonaIntent(text: string): PendingPersona | null {
  for (const p of PERSONA_PATTERNS) {
    const m = text.match(p.pattern)
    if (m) {
      const value = p.extract(m)
      if (value && value.length > 1 && value.length <= 50) {
        return { factKey: p.factKey, factValue: value, label: value }
      }
    }
  }
  return null
}

async function confirmPersona() {
  if (!pendingPersona.value) return
  const { factKey, factValue } = pendingPersona.value
  pendingPersona.value = null
  const res: any = await request.post('/user/persona', { factKey, factValue, source: 'ai' })
  messages.value.push({
    role: 'assistant',
    content: res.success ? `✓ 已记录到你的画像` : '记录失败，请稍后重试'
  })
  await scrollBottom()
}

function cancelPersona() {
  pendingPersona.value = null
  messages.value.push({ role: 'assistant', content: '好的，不记录了。' })
}

onMounted(() => {
  restorePosition()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  window.removeEventListener('pointermove', onPointerMove)
  window.removeEventListener('pointerup', onPointerUp)
})
</script>

<style scoped>
.float-ai {
  position: fixed;
  z-index: 500;
}

.float-trigger {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  font-size: 20px;
  cursor: pointer;
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.35);
  transition: transform 0.18s, box-shadow 0.18s;
}
.float-trigger:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 32px rgba(79, 70, 229, 0.4);
}

.float-panel {
  position: absolute;
  right: 0;
  bottom: 64px;
  width: 340px;
  height: 460px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  border: 1px solid rgba(148, 163, 184, 0.2);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.16);
  backdrop-filter: blur(12px);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.fp-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid rgba(148, 163, 184, 0.14);
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  flex-shrink: 0;
}

.fp-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.fp-expand {
  color: #64748b;
  text-decoration: none;
  font-size: 16px;
  padding: 2px 6px;
  border-radius: 6px;
  transition: background 0.15s;
}
.fp-expand:hover { background: #f1f5f9; }

.fp-close {
  background: none;
  border: none;
  color: #94a3b8;
  font-size: 14px;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 6px;
  transition: background 0.15s;
}
.fp-close:hover { background: #f1f5f9; color: #475569; }

.fp-msgs {
  flex: 1;
  overflow-y: auto;
  padding: 14px 14px 8px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fp-welcome p {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 10px;
}

.fp-suggests {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.fp-suggests button {
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid rgba(99, 102, 241, 0.2);
  background: #f0f4ff;
  color: #4f46e5;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s;
}
.fp-suggests button:hover { background: #e0e7ff; }

.fp-msg {
  display: flex;
}
.fp-msg.user { justify-content: flex-end; }
.fp-msg.assistant { justify-content: flex-start; }

.fp-msg p {
  max-width: 82%;
  padding: 9px 13px;
  border-radius: 14px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
.fp-msg.user p {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  border-bottom-right-radius: 4px;
}
.fp-msg.assistant p {
  background: #f1f5f9;
  color: #1e293b;
  border-bottom-left-radius: 4px;
}

.typing {
  display: flex !important;
  gap: 4px;
  align-items: center;
  padding: 12px 14px !important;
}
.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  animation: bounce 1.2s infinite;
}
.typing span:nth-child(2) { animation-delay: 0.2s; }
.typing span:nth-child(3) { animation-delay: 0.4s; }

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); }
  30% { transform: translateY(-5px); }
}

.fp-input-row {
  display: flex;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
  flex-shrink: 0;
}

.hidden-input {
  display: none;
}

.fp-attach {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: 1px solid #dbe3ef;
  background: #fff;
  cursor: pointer;
  flex-shrink: 0;
}

.fp-input {
  flex: 1;
  padding: 9px 13px;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  font-size: 13px;
  outline: none;
  background: #fff;
  transition: border-color 0.15s;
}
.fp-input:focus { border-color: #6366f1; }

.fp-send {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  transition: opacity 0.15s;
  flex-shrink: 0;
}
.fp-send:disabled { opacity: 0.4; cursor: not-allowed; }

.fp-confirm-card {
  margin: 4px 0;
  padding: 12px 14px;
  border-radius: 14px;
  background: #f0f4ff;
  border: 1px solid rgba(99, 102, 241, 0.2);
}
.fp-confirm-card p {
  font-size: 13px;
  color: #1e293b;
  margin-bottom: 10px;
}
.fp-confirm-btns {
  display: flex;
  gap: 8px;
}
.fp-btn-ok {
  flex: 1;
  padding: 7px;
  border-radius: 8px;
  border: none;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.fp-btn-cancel {
  flex: 1;
  padding: 7px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
}

.persona-card {
  background: #f5f3ff;
  border-color: rgba(124, 58, 237, 0.2);
}
.persona-card strong { color: #7c3aed; }

.travel-card {
  background: #ecfeff;
  border-color: rgba(14, 116, 144, 0.25);
}
.travel-card strong { color: #0f766e; }

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
</style>
