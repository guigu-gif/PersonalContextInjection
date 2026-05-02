<template>
  <div class="settings-page">
    <AppMenu />

    <main class="settings-main">
      <header class="settings-header">
        <p class="eyebrow">Settings</p>
        <h1>个人设置</h1>
        <p class="header-sub">界面偏好和 AI 画像都在这里管理。</p>
      </header>

      <!-- 界面设置 -->
      <section class="settings-card">
        <h2>界面设置</h2>
        <p class="section-sub">修改后立即生效，AI 助手也可以帮你切换。</p>

        <div class="option-group">
          <label class="option-label">身份</label>
          <div class="option-row">
            <button
              v-for="opt in identityOpts" :key="opt.value"
              :class="['opt-btn', profile.identity === opt.value ? 'active' : '']"
              @click="applyAction('SET_IDENTITY', opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>

        <div class="option-group">
          <label class="option-label">字体大小</label>
          <div class="option-row">
            <button
              v-for="opt in fontOpts" :key="opt.value"
              :class="['opt-btn', profile.fontSize === opt.value ? 'active' : '']"
              @click="applyAction('SET_FONT_SIZE', opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>

        <div class="option-group">
          <label class="option-label">主题</label>
          <div class="option-row">
            <button
              v-for="opt in themeOpts" :key="opt.value"
              :class="['opt-btn', 'theme-btn', profile.theme === opt.value ? 'active' : '']"
              :data-theme-preview="opt.value"
              @click="applyAction('SET_THEME', opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>

        <div class="option-group">
          <label class="option-label">壁纸 <span class="tag-reserved">预留</span></label>
          <div class="option-row">
            <button
              v-for="opt in wallpaperOpts" :key="opt.value"
              :class="['opt-btn', profile.wallpaper === opt.value ? 'active' : '']"
              @click="applyAction('SET_WALLPAPER', opt.value)"
            >{{ opt.label }}</button>
          </div>
        </div>
      </section>

      <!-- 用户画像 -->
      <section class="settings-card">
        <div class="card-head">
          <div>
            <h2>AI 用户画像</h2>
            <p class="section-sub">AI 会把这些信息注入对话上下文，让回答更贴合你。你可以随时删除任意条目。</p>
          </div>
          <button v-if="personaList.length" class="danger-btn" @click="confirmClearAll">清空全部</button>
        </div>

        <div class="persona-add">
          <select v-model="newKey" class="persona-select">
            <option v-for="k in factKeys" :key="k" :value="k">{{ k }}</option>
          </select>
          <input v-model="newValue" class="persona-input" placeholder="输入事实内容，如：软件工程专业大三学生" maxlength="100" @keydown.enter="addFact" />
          <button class="solid-btn" :disabled="!newValue.trim()" @click="addFact">添加</button>
        </div>

        <div v-if="!personaList.length" class="empty-state">
          还没有画像条目。添加后 AI 对话会更了解你。
        </div>
        <div v-else class="persona-groups">
          <div v-for="group in groupedPersona" :key="group.key" class="persona-group">
            <p class="group-label">{{ group.key }}</p>
            <div class="persona-list">
              <div v-for="item in group.items" :key="item.id" class="persona-item">
                <span class="persona-value">{{ item.factValue }}</span>
                <div class="persona-right">
                  <span :class="['source-tag', item.source]">{{ item.source === 'ai' ? 'AI提取' : '手动' }}</span>
                  <button class="icon-del" @click="deleteFact(item.id)">✕</button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <p class="privacy-note">
          ✦ 画像数据仅用于本系统 AI 对话上下文，不会上传至任何第三方服务。
        </p>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import AppMenu from '@/components/AppMenu.vue'
import request from '@/utils/request'
import { useProfileStore } from '@/stores/profile'
import { storeToRefs } from 'pinia'

const profileStore = useProfileStore()
const { profile } = storeToRefs(profileStore)

type PersonaItem = { id: number; factKey: string; factValue: string; source: string }

const personaList = ref<PersonaItem[]>([])
const newKey = ref('身份')
const newValue = ref('')

const factKeys = ['身份', '偏好', '关注', '其他']

const identityOpts = [
  { value: 'general', label: '普通用户' },
  { value: 'student', label: '学生' },
  { value: 'elder',   label: '老年人' },
]
const fontOpts = [
  { value: 'normal', label: '正常' },
  { value: 'large',  label: '大' },
  { value: 'xlarge', label: '超大' },
]
const themeOpts = [
  { value: 'default', label: '默认' },
  { value: 'elder',   label: '老年' },
  { value: 'dark',    label: '暗色' },
]
const wallpaperOpts = [
  { value: 'none',    label: '无' },
  { value: 'campus',  label: '校园' },
  { value: 'nature',  label: '自然' },
  { value: 'minimal', label: '简约' },
]

async function applyAction(action: string, value: string) {
  await profileStore.applyAction(action, value)
}

async function loadPersona() {
  const res: any = await request.get('/user/persona')
  if (res.success) personaList.value = res.data ?? []
}

const groupedPersona = computed(() => {
  const order = ['身份', '偏好', '关注', '其他']
  return order
    .map(key => ({ key, items: personaList.value.filter(p => p.factKey === key) }))
    .filter(g => g.items.length > 0)
})

async function addFact() {
  if (!newValue.value.trim()) return
  const res: any = await request.post('/user/persona', {
    factKey: newKey.value,
    factValue: newValue.value.trim(),
    source: 'manual',
  })
  if (res.success) {
    personaList.value.unshift(res.data)
    newValue.value = ''
  } else {
    alert(res.errorMsg || '添加失败')
  }
}

async function deleteFact(id: number) {
  const res: any = await request.delete(`/user/persona/${id}`)
  if (res.success) personaList.value = personaList.value.filter(p => p.id !== id)
}

async function confirmClearAll() {
  if (!confirm('确认清空所有画像条目？AI 将不再有关于你的记忆。')) return
  const res: any = await request.delete('/user/persona/clear')
  if (res.success) personaList.value = []
}

onMounted(() => {
  profileStore.load()
  loadPersona()
})
</script>

<style scoped>
.settings-page {
  min-height: 100vh;
  padding: 72px 32px 48px;
  background:
    radial-gradient(circle at top left, rgba(79, 70, 229, 0.08), transparent 30%),
    linear-gradient(160deg, #f0f4ff 0%, #f7f8fc 60%, #eef6f8 100%);
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

.settings-main {
  max-width: 720px;
  margin: 0 auto;
}

.settings-header {
  margin-bottom: 28px;
}
.eyebrow {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #6366f1;
  margin-bottom: 6px;
}
.settings-header h1 {
  font-size: clamp(28px, 4vw, 38px);
  color: #1e293b;
  margin-bottom: 6px;
}
.header-sub { color: #64748b; font-size: 15px; }

.settings-card {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 24px;
  padding: 28px;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.06);
  margin-bottom: 20px;
}

.settings-card h2 {
  font-size: 20px;
  color: #1e293b;
  margin-bottom: 4px;
}
.section-sub {
  font-size: 13px;
  color: #94a3b8;
  margin-bottom: 22px;
}

.option-group {
  margin-bottom: 20px;
}
.option-label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  margin-bottom: 10px;
}
.option-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.opt-btn {
  padding: 9px 18px;
  border-radius: 12px;
  border: 1.5px solid #e2e8f0;
  background: #fff;
  color: #475569;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.15s;
}
.opt-btn:hover { border-color: #6366f1; color: #4f46e5; }
.opt-btn.active {
  border-color: #6366f1;
  background: #f0f4ff;
  color: #4f46e5;
  font-weight: 600;
}

.tag-reserved {
  font-size: 11px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #94a3b8;
  margin-left: 6px;
  vertical-align: middle;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 20px;
}

.persona-add {
  display: flex;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}
.persona-select {
  padding: 10px 12px;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  background: #fff;
  color: #1e293b;
  outline: none;
  flex-shrink: 0;
}
.persona-input {
  flex: 1;
  min-width: 200px;
  padding: 10px 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 12px;
  font-size: 14px;
  outline: none;
  background: #fff;
  transition: border-color 0.15s;
}
.persona-input:focus { border-color: #6366f1; }

.solid-btn {
  padding: 10px 20px;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  white-space: nowrap;
  transition: opacity 0.15s;
}
.solid-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.danger-btn {
  padding: 9px 16px;
  border-radius: 12px;
  border: 1px solid rgba(220, 38, 38, 0.2);
  background: #fff1f0;
  color: #dc2626;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s;
  flex-shrink: 0;
}
.danger-btn:hover { background: #fee2e2; }

.persona-groups {
  display: grid;
  gap: 16px;
  margin-bottom: 16px;
}
.persona-group {}
.group-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #94a3b8;
  margin-bottom: 8px;
  padding-left: 4px;
}
.persona-list {
  display: grid;
  gap: 8px;
}
.persona-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 14px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.14);
}
.persona-left {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.persona-key {
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 999px;
  background: #e0e7ff;
  color: #4f46e5;
  font-weight: 500;
  flex-shrink: 0;
}
.persona-value {
  font-size: 14px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.persona-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.source-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
}
.source-tag.manual { background: #f1f5f9; color: #64748b; }
.source-tag.ai     { background: #f5f3ff; color: #7c3aed; }

.icon-del {
  background: none;
  border: none;
  color: #cbd5e1;
  cursor: pointer;
  font-size: 13px;
  padding: 2px 6px;
  border-radius: 6px;
  transition: color 0.15s, background 0.15s;
}
.icon-del:hover { color: #dc2626; background: #fee2e2; }

.empty-state {
  text-align: center;
  color: #94a3b8;
  padding: 32px 12px;
  font-size: 14px;
}

.privacy-note {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid rgba(148, 163, 184, 0.12);
}
</style>
