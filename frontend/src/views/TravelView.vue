<template>
  <main class="travel-page">
    <AppMenu />
    <section class="card hero-card">
      <p class="eyebrow">出行</p>
      <h1>公交与地铁路线</h1>
      <p class="desc">
        填写起终点查询公交或地铁路线。进入页面将自动获取当前位置作为出发地。
      </p>

      <p v-if="cityHint" class="city-hint">📍 当前城市：{{ cityHint }}</p>

      <div class="form-grid">
        <label>
          <span>出发地</span>
          <input v-model.trim="form.origin" :placeholder="locating ? '定位中…' : '地标或详细地址，如：南京路步行街'" />
        </label>
        <label>
          <span>目的地</span>
          <input v-model.trim="form.destination" placeholder="如：人民广场" />
        </label>
      </div>

      <div class="actions">
        <button class="solid-btn" :disabled="loading" @click="submit">
          {{ loading ? '查询中…' : '查询路线' }}
        </button>
        <button class="ghost-btn" type="button" :disabled="loading" @click="fillDemo">示范线路</button>
        <button class="ghost-btn" type="button" :disabled="locating || loading" @click="useCurrentLocation">
          {{ locating ? '定位中…' : '重新定位' }}
        </button>
      </div>

      <div class="ai-panel">
        <p class="ai-title">AI 智能解析（说出或输入你的出行需求）</p>
        <div class="voice-row">
          <textarea
            v-model.trim="aiInstruction"
            class="ai-input"
            rows="2"
            placeholder="例如：我要去人民广场 / I need to get to Canton Tower"
          />
          <button
            class="voice-btn"
            :class="{ listening: isListening }"
            :title="voiceSupported ? (isListening ? '停止录音' : '点击语音输入') : '当前浏览器不支持语音输入'"
            :disabled="!voiceSupported"
            @click="toggleVoice"
          >
            {{ isListening ? '🔴' : '🎤' }}
          </button>
        </div>
        <p v-if="voiceHint" class="voice-hint">{{ voiceHint }}</p>
        <div class="actions">
          <button class="solid-btn" :disabled="aiLoading || !aiInstruction" @click="aiParse">
            {{ aiLoading ? '解析中…' : 'AI解析' }}
          </button>
          <button class="ghost-btn" :disabled="!aiDraft || aiLoading" @click="aiConfirm">确认执行</button>
        </div>
        <p v-if="aiErrorText" class="error">{{ aiErrorText }}</p>
        <div v-if="aiDraft" class="ai-preview">
          <p>草稿：{{ aiDraft.city }} · {{ aiDraft.origin }} → {{ aiDraft.destination }}</p>
          <p v-if="aiSummary" class="desc">{{ aiSummary }}</p>
        </div>
      </div>

      <p v-if="errorText" class="error">{{ errorText }}</p>
    </section>

    <section class="card">
      <h2>地图预览</h2>
      <p v-if="mapErrorText" class="error">{{ mapErrorText }}</p>
      <div ref="mapRef" class="map-box"></div>
    </section>

    <section v-if="result" class="card">
      <h2>推荐路线</h2>
      <p class="desc route-line">
        <span class="route-city">{{ result.city }}</span>
        <span class="route-arrow">{{ result.origin }}</span>
        <span class="route-sep">→</span>
        <span class="route-arrow">{{ result.destination }}</span>
      </p>

      <div v-if="!result.routes?.length" class="empty">未查到可用路线</div>

      <article v-for="(route, index) in result.routes" :key="index" class="route-item">
        <header>
          <strong>方案 {{ index + 1 }}</strong>
          <span>{{ route.duration }} · {{ route.distance }} · {{ route.cost }}</span>
        </header>
        <ol>
          <li v-for="(step, sIndex) in route.steps" :key="`${index}-${sIndex}`">{{ step }}</li>
        </ol>
      </article>
    </section>

    <section v-if="guideList.length" class="card">
      <h2>相关攻略</h2>
      <p class="desc">结合当前行程关键词推荐，含官方与用户投稿；排序综合可信度与互动。</p>
      <article v-for="guide in guideList" :key="guide.id" class="guide-item">
        <header>
          <strong>{{ guide.title }}</strong>
          <span class="badge" :class="guide.isOfficial === 1 ? 'official' : 'ugc'">
            {{ guide.isOfficial === 1 ? '官方' : '用户' }}
          </span>
        </header>
        <p class="guide-meta">
          评分 {{ guide.score }} · 赞 {{ guide.likeCount }} · 收藏 {{ guide.favCount }} · 投币 {{ guide.coinCount }} · 充电 {{ guide.chargeCount }}
        </p>
        <p class="guide-content">{{ guide.content }}</p>
        <div class="actions">
          <button class="ghost-btn" @click="doAction(guide.id, 'LIKE')">{{ guide.liked ? '取消点赞' : '点赞' }}</button>
          <button class="ghost-btn" @click="doAction(guide.id, 'FAV')">{{ guide.favored ? '取消收藏' : '收藏' }}</button>
          <button class="ghost-btn" @click="doAction(guide.id, 'COIN')">投币</button>
          <button class="ghost-btn" @click="doAction(guide.id, 'CHARGE')">充电</button>
        </div>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import request from '@/utils/request'
import AppMenu from '@/components/AppMenu.vue'

type TransitRoute = { duration: string; distance: string; cost: string; steps: string[]; polyline?: string[] }
type TravelResult = { city: string; origin: string; destination: string; routes: TransitRoute[]; originCoord?: string; destCoord?: string }
type GuideCard = { id: number; title: string; content: string; isOfficial: number; score: number; likeCount: number; favCount: number; coinCount: number; chargeCount: number; liked: boolean; favored: boolean }

const form = reactive({ city: '', origin: '', destination: '' })
const cityHint = ref('')
const loading = ref(false)
const errorText = ref('')
const result = ref<TravelResult | null>(null)
const guideList = ref<GuideCard[]>([])
const aiInstruction = ref('')
const aiLoading = ref(false)
const locating = ref(false)
const aiErrorText = ref('')
const aiSummary = ref('')
const aiDraft = ref<{ city: string; origin: string; destination: string } | null>(null)
const route = useRoute()
const mapRef = ref<HTMLElement | null>(null)
const mapErrorText = ref('')

// 语音输入
const isListening = ref(false)
const voiceHint = ref('')
const voiceSupported = !!(window as any).SpeechRecognition || !!(window as any).webkitSpeechRecognition
let recognition: any = null

let amapReady = false
let amapMap: any = null
let currentLngLat: [number, number] | null = null

// ---- 语音输入 ----

function toggleVoice() {
  if (!voiceSupported) return
  if (isListening.value) {
    recognition?.stop()
    return
  }
  const SR = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition
  recognition = new SR()
  recognition.lang = 'zh-CN'
  recognition.continuous = false
  recognition.interimResults = true
  voiceHint.value = '正在聆听…'
  isListening.value = true
  recognition.onresult = (e: any) => {
    const transcript = Array.from(e.results as any[])
      .map((r: any) => r[0].transcript)
      .join('')
    aiInstruction.value = transcript
    voiceHint.value = e.results[e.results.length - 1].isFinal ? '识别完成，可修改后解析' : `识别中：${transcript}`
  }
  recognition.onerror = (e: any) => {
    voiceHint.value = e.error === 'not-allowed' ? '请允许麦克风权限后重试' : `语音识别失败：${e.error}`
    isListening.value = false
  }
  recognition.onend = () => {
    isListening.value = false
    if (!aiInstruction.value) voiceHint.value = '未识别到内容，请重试'
  }
  recognition.start()
}

// ---- 路线查询 ----

function fillDemo() {
  form.city = '上海'
  form.origin = '南京路步行街'
  form.destination = '人民广场'
}

async function submit() {
  errorText.value = ''
  result.value = null
  guideList.value = []
  if (!form.origin || !form.destination) {
    errorText.value = '出发地和目的地都要填'
    return
  }
  loading.value = true
  try {
    const res: any = await request.post('/travel/route', {
      city: form.city, origin: form.origin, destination: form.destination
    })
    if (!res?.success) { errorText.value = res?.errorMsg || '查询失败'; return }
    result.value = res.data
    const r0 = Array.isArray(res.data?.routes) && res.data.routes.length > 0 ? res.data.routes[0] : null
    await loadGuides()
    triggerMapUpdate(res.data?.originCoord, res.data?.destCoord, r0?.polyline)
  } catch (e: any) {
    errorText.value = e?.message || '网络异常或服务未就绪，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadGuides() {
  if (!form.city) return
  try {
    const res: any = await request.get('/guide/recommend', {
      params: { city: form.city, origin: form.origin, destination: form.destination, topK: 5 }
    })
    if (res?.success) guideList.value = res.data || []
  } catch { /* ignore */ }
}

async function doAction(guideId: number, actionType: 'LIKE' | 'FAV' | 'COIN' | 'CHARGE') {
  try {
    const res: any = await request.post(`/guide/${guideId}/action`, { actionType })
    if (!res?.success || !res?.data) return
    guideList.value = guideList.value.map(item => item.id === guideId ? { ...item, ...(res.data as GuideCard) } : item)
  } catch { /* ignore */ }
}

async function aiParse() {
  aiErrorText.value = ''
  aiSummary.value = ''
  aiDraft.value = null
  if (!aiInstruction.value) return
  aiLoading.value = true
  try {
    const res: any = await request.post('/travel/ai-parse', { instruction: aiInstruction.value })
    if (!res?.success) { aiErrorText.value = res?.errorMsg || 'AI解析失败'; return }
    const data = res?.data || {}
    aiSummary.value = data.summary || ''
    if (data.draft) {
      aiDraft.value = data.draft
      form.city = data.draft.city || form.city
      form.origin = data.draft.origin || form.origin
      form.destination = data.draft.destination || ''
      if (data.draft.city) cityHint.value = data.draft.city
    }
    if (data.unresolved?.length) aiErrorText.value = data.unresolved.join('；')
    if (data.preview) {
      result.value = data.preview
      await loadGuides()
      const r0 = Array.isArray(data.preview?.routes) && data.preview.routes.length > 0 ? data.preview.routes[0] : null
      triggerMapUpdate(data.preview?.originCoord, data.preview?.destCoord, r0?.polyline)
    }
  } catch (e: any) {
    aiErrorText.value = e?.message || 'AI解析异常'
  } finally {
    aiLoading.value = false
  }
}

async function aiConfirm() {
  if (!aiDraft.value) return
  aiLoading.value = true
  aiErrorText.value = ''
  try {
    const res: any = await request.post('/travel/ai-confirm', { route: aiDraft.value })
    if (!res?.success) { aiErrorText.value = res?.errorMsg || '确认失败'; return }
    const payload = res.data || {}
    if (payload.result) {
      result.value = payload.result
      await loadGuides()
      const r0 = Array.isArray(payload.result?.routes) && payload.result.routes.length > 0 ? payload.result.routes[0] : null
      triggerMapUpdate(payload.result?.originCoord, payload.result?.destCoord, r0?.polyline)
    }
    aiSummary.value = payload.message || '已确认'
  } catch (e: any) {
    aiErrorText.value = e?.message || '确认异常'
  } finally {
    aiLoading.value = false
  }
}

// ---- 地图 ----

async function ensureAmapReady() {
  if (amapReady) return true
  const key = (import.meta as any).env?.VITE_AMAP_WEB_KEY || ''
  if (!key) { mapErrorText.value = '未配置地图 Key（VITE_AMAP_WEB_KEY）'; return false }
  try {
    if (!(window as any).AMap) {
      await new Promise<void>((resolve, reject) => {
        const s = document.createElement('script')
        s.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}`
        s.async = true
        s.onload = () => resolve()
        s.onerror = () => reject(new Error('地图 SDK 加载失败'))
        document.head.appendChild(s)
      })
    }
    if (!(window as any).AMap || !mapRef.value) { mapErrorText.value = '地图初始化失败'; return false }
    amapMap = new (window as any).AMap.Map(mapRef.value, { zoom: 12, center: [121.4737, 31.2304] })
    amapReady = true
    mapErrorText.value = ''
    return true
  } catch (e: any) {
    mapErrorText.value = e?.message || '地图初始化失败'
    return false
  }
}

function triggerMapUpdate(originCoord?: string, destCoord?: string, polyline?: string[]) {
  Promise.resolve()
    .then(() => updateMapByCoords(originCoord, destCoord, polyline))
    .catch((e: any) => { mapErrorText.value = e?.message || '地图更新失败' })
}

function parseLngLat(coord: string): [number, number] | null {
  if (!coord) return null
  const p = coord.split(',')
  if (p.length !== 2) return null
  const lng = parseFloat(p[0] ?? ''), lat = parseFloat(p[1] ?? '')
  return isNaN(lng) || isNaN(lat) ? null : [lng, lat]
}

async function updateMapByCoords(originCoord?: string, destCoord?: string, polylineCoords?: string[]) {
  const ok = await ensureAmapReady()
  if (!ok) return
  try {
    const AMap = (window as any).AMap
    amapMap.clearMap()
    const startPos = currentLngLat || parseLngLat(originCoord || '')
    const endPos = parseLngLat(destCoord || '')

    if (polylineCoords && polylineCoords.length >= 2) {
      const path = polylineCoords.map(p => parseLngLat(p)).filter(Boolean) as [number, number][]
      if (path.length >= 2) {
        amapMap.add(new AMap.Polyline({
          path, strokeColor: '#3b82f6', strokeWeight: 5, strokeOpacity: 0.9, lineJoin: 'round', lineCap: 'round'
        }))
      }
    }

    if (startPos) amapMap.add(new AMap.Marker({ position: startPos, title: form.origin || '起点', label: { content: '起', direction: 'top' }, zIndex: 110 }))
    if (endPos) amapMap.add(new AMap.Marker({ position: endPos, title: form.destination || '终点', label: { content: '终', direction: 'top' }, zIndex: 110 }))

    if (startPos && endPos) amapMap.setFitView(undefined, false, [60, 60, 60, 60])
    else if (startPos) { amapMap.setCenter(startPos); amapMap.setZoom(15) }

    mapErrorText.value = ''
  } catch (e: any) {
    mapErrorText.value = e?.message || '地图更新失败'
  }
}

// ---- GPS 定位 ----

async function useCurrentLocation() {
  errorText.value = ''
  if (!navigator.geolocation) { errorText.value = '当前浏览器不支持定位功能'; return }
  locating.value = true
  try {
    const position = await new Promise<GeolocationPosition>((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 })
    })
    const { latitude, longitude, accuracy } = position.coords
    currentLngLat = [longitude, latitude]
    const res: any = await request.post('/travel/locate', { latitude, longitude, cityHint: form.city })
    if (!res?.success) { errorText.value = res?.errorMsg || '定位解析失败'; return }
    const data = res.data || {}
    form.origin = String(data.origin || '当前位置')
    if (data.city) {
      form.city = String(data.city).replace(/市$/, '')
      cityHint.value = form.city
    }
    const acc = Math.round(accuracy || 0)
    if (acc >= 3000) errorText.value = `定位精度较低（约${acc}米），建议在室外开启GPS后重试。`
    if (form.destination) { await submit() } else { triggerMapUpdate() }
  } catch (e: any) {
    const code = (e as any)?.code
    if (code === 1) errorText.value = '已拒绝定位授权，请允许后重试'
    else if (code === 2) errorText.value = '无法获取当前位置，请检查定位服务'
    else if (code === 3) errorText.value = '定位超时，请重试或手动填写出发地'
    else errorText.value = '定位失败，请手动填写出发地'
  } finally {
    locating.value = false
  }
}

onMounted(async () => {
  await ensureAmapReady()
  const city = String(route.query.city || '').trim()
  const origin = String(route.query.origin || '').trim()
  const destination = String(route.query.destination || '').trim()
  const from = String(route.query.from || '').trim()
  if (city || origin || destination) {
    // 从 AI 助手跳转带入参数时，不自动再触发定位
    form.city = city; form.origin = origin; form.destination = destination
    if (city) cityHint.value = city
    aiDraft.value = { city, origin, destination }
    if (['float-ai', 'float-ai-text', 'chat-text', 'chat-image'].includes(from)) {
      aiSummary.value = '已从AI助手带入行程草稿，请确认后执行。'
    }
    await submit()
  } else {
    // 正常进入：自动定位
    useCurrentLocation()
  }
})
</script>

<style scoped>
.travel-page {
  max-width: 920px;
  margin: 0 auto;
  padding: 48px 16px 28px;
  display: grid;
  gap: 18px;
  min-height: 100vh;
  box-sizing: border-box;
  background:
    radial-gradient(ellipse 80% 50% at 50% -20%, rgba(59, 130, 246, 0.12), transparent),
    linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.card { background: #fff; border-radius: 16px; padding: 20px 22px; box-shadow: 0 8px 28px rgba(15, 23, 42, 0.06); border: 1px solid rgba(148, 163, 184, 0.12); }
.hero-card h1 { font-size: 1.5rem; letter-spacing: -0.02em; }

.eyebrow { margin: 0 0 4px; font-size: 11px; letter-spacing: 0.12em; text-transform: uppercase; color: #3b82f6; font-weight: 600; }

.city-hint { margin: 10px 0 0; font-size: 13px; color: #475569; }

h1, h2 { margin: 0; }
h2 { font-size: 1.125rem; color: #0f172a; }

.desc { margin: 10px 0 0; color: #64748b; font-size: 14px; line-height: 1.65; max-width: 52em; }

.route-line { display: flex; flex-wrap: wrap; align-items: center; gap: 6px 10px; margin-top: 12px; font-size: 13px; color: #475569; }
.route-city { color: #94a3b8; font-size: 12px; }
.route-arrow { font-weight: 600; color: #1e293b; }
.route-sep { color: #94a3b8; }

.form-grid { margin-top: 14px; display: grid; gap: 12px; }
label span { display: block; margin-bottom: 6px; color: #334155; font-size: 14px; }

input {
  width: 100%;
  border: 1px solid #d8e0ef;
  border-radius: 12px;
  padding: 10px 12px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}
input:focus { border-color: #60a5fa; }

.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }

.solid-btn, .ghost-btn { border: 0; border-radius: 10px; padding: 9px 14px; cursor: pointer; font-size: 14px; }
.solid-btn { background: #3b82f6; color: #fff; }
.solid-btn:disabled, .ghost-btn:disabled { opacity: 0.7; cursor: not-allowed; }
.ghost-btn { background: #eef2ff; color: #1e293b; }

.error { margin-top: 10px; color: #ef4444; font-size: 14px; }

.ai-panel { margin-top: 14px; padding: 14px; border: 1px dashed #cbd5e1; border-radius: 12px; background: #f8fafc; }
.ai-title { font-size: 13px; font-weight: 600; color: #334155; margin-bottom: 8px; }

.voice-row { display: flex; gap: 8px; align-items: flex-start; }
.ai-input { flex: 1; border: 1px solid #d8e0ef; border-radius: 10px; padding: 10px; resize: vertical; min-height: 62px; font-size: 14px; outline: none; }
.ai-input:focus { border-color: #60a5fa; }

.voice-btn {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border: 2px solid #d8e0ef;
  border-radius: 50%;
  background: #fff;
  font-size: 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s, background 0.2s;
}
.voice-btn:hover:not(:disabled) { border-color: #3b82f6; background: #eff6ff; }
.voice-btn.listening { border-color: #ef4444; background: #fef2f2; animation: pulse 1s ease-in-out infinite; }
.voice-btn:disabled { opacity: 0.4; cursor: not-allowed; }

@keyframes pulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.1); }
}

.voice-hint { margin-top: 6px; font-size: 12px; color: #64748b; }
.ai-preview { margin-top: 8px; font-size: 13px; color: #334155; }

.empty { margin-top: 12px; color: #64748b; }

.route-item { margin-top: 14px; border: 1px solid #e2e8f0; border-radius: 14px; padding: 14px 16px; background: #fafbfc; }
.route-item header { display: flex; justify-content: space-between; gap: 10px; color: #0f172a; flex-wrap: wrap; }
.route-item ol { margin: 10px 0 0; padding-left: 20px; color: #334155; }

.guide-item { margin-top: 12px; border: 1px solid #e2e8f0; border-radius: 12px; padding: 12px; }
.guide-item header { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.badge { font-size: 12px; border-radius: 999px; padding: 2px 8px; }
.badge.official { background: #dbeafe; color: #1d4ed8; }
.badge.ugc { background: #f1f5f9; color: #475569; }
.guide-meta { margin-top: 6px; font-size: 13px; color: #64748b; }
.guide-content { margin-top: 8px; color: #334155; line-height: 1.7; }

.map-box { margin-top: 12px; width: 100%; height: 360px; border: 1px solid #dbe3f3; border-radius: 12px; overflow: hidden; }
</style>
