<template>
  <div class="schedule-wrap">
    <AppMenu />

    <!-- 顶部控制栏 -->
    <div class="top-bar">
      <div class="semester-area">
        <select v-model="currentSemesterId" class="semester-select" @change="onSemesterChange">
          <option v-if="!semesters.length" :value="null">暂无学期</option>
          <option v-for="s in semesters" :key="s.id" :value="s.id">{{ s.name }}</option>
        </select>
        <button class="btn-sm btn-outline" @click="showSemesterForm=true">+ 新建学期</button>
        <button v-if="currentSemesterId" class="btn-sm btn-del-sem" @click="deleteSemester">删除</button>
      </div>
      <div class="week-area">
        <button class="btn-week-nav" @click="changeWeek(-1)" :disabled="currentWeek<=1">‹</button>
        <span class="week-label">第 {{ currentWeek }} 周</span>
        <button class="btn-week-nav" @click="changeWeek(1)" :disabled="currentWeek>=totalWeeks">›</button>
      </div>
      <div class="action-area">
        <button class="btn-slot" @click="openSlotConfig">⚙ 节次</button>
        <button class="btn-list" @click="openCourseList">☰ 课程列表</button>
        <button class="btn-ai" @click="showAiPanel=!showAiPanel">✦ AI 修改</button>
        <button class="btn-add" @click="openAddForm">+ 手动添加</button>
      </div>
    </div>

    <!-- AI 面板 -->
    <div v-if="showAiPanel" class="ai-panel">
      <div class="ai-tabs">
        <button :class="['ai-tab', aiMode==='text'?'active':'']" @click="aiMode='text'">文字指令</button>
        <button :class="['ai-tab', aiMode==='image'?'active':'']" @click="aiMode='image'">识图导入</button>
      </div>
      <div v-if="aiMode==='text'" class="ai-input-row">
        <input v-model="aiInstruction" class="ai-input" placeholder="如：帮我删掉周三第3节的课" @keydown.enter="handleAiParse" />
        <button class="btn-parse" :disabled="aiLoading" @click="handleAiParse">{{ aiLoading?'解析中...':'解析' }}</button>
      </div>
      <div v-if="aiMode==='image'" class="ai-image-row">
        <label class="upload-label">
          <input type="file" accept="image/*" class="file-input" @change="handleFileChange" />
          <span v-if="!imageFile">📷 点击选择课程表图片（jpg/png，≤5MB）</span>
          <span v-else class="file-selected">✓ {{ imageFile.name }}</span>
        </label>
        <button class="btn-parse" :disabled="aiLoading||!imageFile" @click="handleImageParse">{{ aiLoading?'识别中...':'识别' }}</button>
      </div>
      <p class="ai-tip">AI 会先展示操作预览，你确认后才会执行</p>
    </div>

    <!-- 课程表 -->
    <div class="timetable">
      <div class="tt-head">
        <div class="tt-time-th">节次</div>
        <div v-for="d in days" :key="d.val" class="tt-day-th"
          :class="{'is-today': d.val===todayWeekday}">
          {{ d.label }}
        </div>
      </div>
      <div class="tt-body">
        <!-- 左侧时间轴 -->
        <div class="tt-time-col">
          <div v-for="s in SLOTS" :key="s.slot" class="tt-time-cell">
            <b>{{ s.slot }}</b>
            <span>{{ s.start }}</span>
            <span>{{ s.end }}</span>
          </div>
        </div>
        <!-- 7列课程，每列独立 -->
        <div v-for="d in days" :key="d.val" class="tt-day-col"
          :class="{'is-future': d.val>todayWeekday}">
          <div v-for="s in SLOTS.length" :key="s" class="tt-slot-cell">
            <template v-if="getCourseStart(d.val, s)">
              <div class="course-block"
                :style="courseBlockStyle(getCourseStart(d.val, s))"
                @click="openEditForm(getCourseStart(d.val, s))">
                <span class="cb-name">{{ getCourseStart(d.val, s).name }}</span>
                <span v-if="getCourseStart(d.val, s).location" class="cb-sub">{{ getCourseStart(d.val, s).location.split('[')[0] }}</span>
                <span v-if="getCourseStart(d.val, s).teacher" class="cb-sub">{{ getCourseStart(d.val, s).teacher }}</span>
                <span class="cb-week">{{ getCourseStart(d.val, s).weekStart }}-{{ getCourseStart(d.val, s).weekEnd }}周</span>
              </div>
            </template>
          </div>
        </div>
      </div>
    </div>

    <!-- 新建学期弹窗 -->
    <div v-if="showSemesterForm" class="modal-mask" @click.self="showSemesterForm=false">
      <div class="modal">
        <h3>新建学期</h3>
        <div class="form-grid">
          <label>名称</label><input v-model="semForm.name" placeholder="大一上学期" />
          <label>开始日期</label><input v-model="semForm.startDate" type="date" />
          <label>总周数</label><input v-model.number="semForm.totalWeeks" type="number" min="1" max="30" />
        </div>
        <div class="modal-btns">
          <button class="btn-confirm" @click="createSemester">保存</button>
          <button class="btn-cancel" @click="showSemesterForm=false">取消</button>
        </div>
      </div>
    </div>

    <!-- 添加/编辑课程弹窗 -->
    <div v-if="showAddForm" class="modal-mask" @click.self="showAddForm=false">
      <div class="modal">
        <h3>{{ editingId ? '编辑课程' : '添加课程' }}</h3>
        <div class="form-grid">
          <label>课程名</label><input v-model="form.name" placeholder="高等数学" />
          <label>星期</label>
          <select v-model="form.weekday">
            <option v-for="d in days" :key="d.val" :value="d.val">{{ d.label }}</option>
          </select>
          <label>开始节次</label><input v-model.number="form.startSlot" type="number" min="1" max="14" />
          <label>结束节次</label><input v-model.number="form.endSlot" type="number" min="1" max="14" />
          <label>开始周</label><input v-model.number="form.weekStart" type="number" min="1" max="30" />
          <label>结束周</label><input v-model.number="form.weekEnd" type="number" min="1" max="30" />
          <label>地点</label><input v-model="form.location" placeholder="A101" />
          <label>教师</label><input v-model="form.teacher" placeholder="张老师" />
          <label>颜色</label><input v-model="form.color" type="color" />
        </div>
        <div class="modal-btns">
          <button class="btn-confirm" @click="saveCourse">保存</button>
          <button v-if="editingId" class="btn-danger" @click="doDeleteEditing">删除</button>
          <button class="btn-cancel" @click="showAddForm=false">取消</button>
        </div>
      </div>
    </div>

    <!-- AI 确认弹窗 -->
    <div v-if="showAiConfirm" class="modal-mask" @click.self="showAiConfirm=false">
      <div class="modal modal-wide">
        <h3>AI 操作预览</h3>
        <p class="ai-summary">{{ aiSummary }}</p>
        <div v-if="parseResult.unresolved?.length" class="unresolved-box">
          <span class="unresolved-label">未能识别：</span>
          <span v-for="u in parseResult.unresolved" :key="u" class="unresolved-item">{{ u }}</span>
        </div>
        <div v-if="pendingOps.length" class="preview-table-wrap">
          <table class="preview-table">
            <thead><tr><th>操作</th><th>课程名</th><th>星期</th><th>节次</th><th>开始周</th><th>结束周</th><th>地点</th><th>移除</th></tr></thead>
            <tbody>
              <tr v-for="(op,idx) in pendingOps" :key="idx">
                <td><span :class="['op-badge',op.action.toLowerCase()]">{{ actionLabel(op.action) }}</span></td>
                <td>{{ op.course?.name||'-' }}</td>
                <td>{{ dayLabel(op.course?.weekday) }}</td>
                <td>{{ op.course?`${op.course.startSlot}-${op.course.endSlot}节`:'-' }}</td>
                <td><input v-if="op.course" v-model.number="op.course.weekStart" type="number" min="1" max="30" class="week-input" /></td>
                <td><input v-if="op.course" v-model.number="op.course.weekEnd" type="number" min="1" max="30" class="week-input" /></td>
                <td>{{ op.course?.location||'-' }}</td>
                <td><button class="btn-remove-op" @click="pendingOps.splice(idx,1)">✕</button></td>
              </tr>
            </tbody>
          </table>
          <p class="preview-hint">周次识别可能有偏差，可直接在表格内修改后再执行</p>
        </div>
        <div v-else class="empty-ops">没有可执行的操作</div>
        <div class="modal-btns">
          <button class="btn-confirm" :disabled="!pendingOps.length||confirmLoading" @click="handleAiConfirm">
            {{ confirmLoading?'执行中...':`确认执行（${pendingOps.length}条）` }}
          </button>
          <button class="btn-cancel" @click="showAiConfirm=false">取消</button>
        </div>
      </div>
    </div>

    <!-- 课程列表抽屉 -->
    <div v-if="showCourseList" class="drawer-mask" @click.self="showCourseList=false">
      <div class="drawer drawer-centered">
        <div class="drawer-header">
          <h3>全部课程（{{ courseListDraft.length }}条）</h3>
          <button class="drawer-close" @click="showCourseList=false">✕</button>
        </div>
        <div class="drawer-body">
          <table class="list-table">
            <thead>
              <tr><th>课程名</th><th>星期</th><th>开始节</th><th>结束节</th><th>开始周</th><th>结束周</th><th>教室</th><th>教师</th><th>操作</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in courseListDraft" :key="c.id">
                <td>{{ c.name }}</td>
                <td>{{ dayLabel(c.weekday) }}</td>
                <td><input v-model.number="c.startSlot" type="number" min="1" max="14" class="week-input" /></td>
                <td><input v-model.number="c.endSlot" type="number" min="1" max="14" class="week-input" /></td>
                <td><input v-model.number="c.weekStart" type="number" min="1" max="30" class="week-input" /></td>
                <td><input v-model.number="c.weekEnd" type="number" min="1" max="30" class="week-input" /></td>
                <td><input v-model="c.location" class="loc-input" placeholder="-" /></td>
                <td class="td-teacher">{{ c.teacher||'-' }}</td>
                <td class="list-actions">
                  <button class="btn-save-row" @click="saveRow(c)">保存</button>
                  <button class="btn-remove-op" @click="deleteRow(c)">✕</button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 节次配置弹窗 -->
    <div v-if="showSlotConfig" class="modal-mask" @click.self="showSlotConfig=false">
      <div class="modal modal-slot">
        <h3>节次时间配置</h3>
        <div class="slot-table-wrap">
          <table class="slot-table">
            <thead><tr><th>节次</th><th>开始</th><th>结束</th><th></th></tr></thead>
            <tbody>
              <tr v-for="(s, idx) in slotDraft" :key="idx">
                <td>{{ s.slot }}</td>
                <td><input v-model="s.start" class="time-input" placeholder="08:00" /></td>
                <td><input v-model="s.end" class="time-input" placeholder="08:45" /></td>
                <td><button class="btn-remove-op" @click="slotDraft.splice(idx,1); reindexSlots()">✕</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <button class="btn-add-slot" @click="addSlotRow">+ 添加节次</button>
        <div class="modal-btns">
          <button class="btn-confirm" @click="saveSlotConfig">保存</button>
          <button class="btn-cancel" @click="showSlotConfig=false">取消</button>
        </div>
      </div>
    </div>

    <!-- slotsUpdated 提示 -->
    <div v-if="slotsUpdatedToast" class="slots-toast">✓ 节次时间已根据课程表更新</div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, type CSSProperties } from 'vue'
import AppMenu from '@/components/AppMenu.vue'
import request from '@/utils/request'

const days = [
  {val:1,label:'周一'},{val:2,label:'周二'},{val:3,label:'周三'},
  {val:4,label:'周四'},{val:5,label:'周五'},{val:6,label:'周六'},{val:7,label:'周日'}
]


const DEFAULT_SLOTS = [
  {slot:1, start:'08:00',end:'08:45'},{slot:2, start:'08:55',end:'09:40'},
  {slot:3, start:'10:00',end:'10:45'},{slot:4, start:'10:55',end:'11:40'},
  {slot:5, start:'14:00',end:'14:45'},{slot:6, start:'14:55',end:'15:40'},
  {slot:7, start:'16:00',end:'16:45'},{slot:8, start:'16:55',end:'17:40'},
  {slot:9, start:'19:00',end:'19:45'},{slot:10,start:'19:55',end:'20:40'},
  {slot:11,start:'20:50',end:'21:35'},{slot:12,start:'21:45',end:'22:30'},
  {slot:13,start:'22:40',end:'23:25'},
]
const SLOTS = ref<{slot:number,start:string,end:string}[]>([...DEFAULT_SLOTS])

async function loadSlots() {
  try {
    const res: any = await request.get('/slot-config')
    if (res.success && res.data) SLOTS.value = JSON.parse(res.data)
  } catch { /* 用默认值 */ }
}

const CELL_H = 80
const COLOR_POOL = ['#4f46e5','#0891b2','#059669','#d97706','#dc2626','#7c3aed','#db2777','#0284c7','#65a30d','#c2410c']

const jsDay = new Date().getDay()
const todayWeekday = jsDay === 0 ? 7 : jsDay

const semesters = ref<any[]>([])
const currentSemesterId = ref<number|null>(null)
const currentWeek = ref(1)
const totalWeeks = ref(20)
const courses = ref<any[]>([])

// 合并同天同名连续节次的课程（仅用于显示）
const displayCourses = computed(() => {
  const result: any[] = []
  const byDayName: Record<string, any[]> = {}
  for (const c of courses.value) {
    const key = `${c.weekday}_${c.name}`
    if (!byDayName[key]) byDayName[key] = []
    byDayName[key].push(c)
  }
  for (const group of Object.values(byDayName)) {
    group.sort((a, b) => a.startSlot - b.startSlot)
    let merged = { ...group[0] }
    for (let i = 1; i < group.length; i++) {
      if (group[i].startSlot === merged.endSlot + 1) {
        merged = { ...merged, endSlot: group[i].endSlot }
      } else {
        result.push(merged)
        merged = { ...group[i] }
      }
    }
    result.push(merged)
  }
  return result
})

function getCourseStart(weekday: number, slot: number) {
  return displayCourses.value.find(c => c.weekday === weekday && c.startSlot === slot) ?? null
}

function courseBlockStyle(c: any): CSSProperties {
  const span = c.endSlot - c.startSlot + 1
  const height = span * CELL_H - 6
  const bg = c.color && c.color !== '#4f46e5' ? c.color : COLOR_POOL[(c.id ?? 0) % COLOR_POOL.length]
  return { height: `${height}px`, background: bg, position: 'absolute', top: '2px', left: '2px', right: '2px', zIndex: 1 }
}

const showSemesterForm = ref(false)
const semForm = ref({ name: '', startDate: '', totalWeeks: 20 })

async function loadSemesters() {
  const res: any = await request.get('/semester/list')
  if (!res.success) return
  semesters.value = res.data ?? []
  const cur = semesters.value.find((s: any) => s.isCurrent === 1)
  if (cur) { currentSemesterId.value = cur.id; await loadCurrentWeek() }
  else if (semesters.value.length) currentSemesterId.value = semesters.value[0].id
}

async function loadCurrentWeek() {
  const res: any = await request.get('/semester/current-week')
  if (res.success && res.data) { currentWeek.value = res.data.week; totalWeeks.value = res.data.totalWeeks }
}

async function onSemesterChange() {
  if (!currentSemesterId.value) return
  await request.put(`/semester/${currentSemesterId.value}/current`)
  await loadCurrentWeek()
  await loadCourses()
}

async function createSemester() {
  if (!semForm.value.name || !semForm.value.startDate) return
  const res: any = await request.post('/semester', semForm.value)
  if (res.success) {
    showSemesterForm.value = false
    semForm.value = { name: '', startDate: '', totalWeeks: 20 }
    currentSemesterId.value = res.data
    await request.put(`/semester/${res.data}/current`)
    await loadSemesters()
    await loadCurrentWeek()
    await loadCourses()
  }
}

function changeWeek(delta: number) {
  const next = currentWeek.value + delta
  if (next < 1 || next > totalWeeks.value) return
  currentWeek.value = next
  loadCourses()
}

async function loadCourses() {
  const params: any = {}
  if (currentSemesterId.value) params.semesterId = currentSemesterId.value
  if (currentWeek.value) params.week = currentWeek.value
  const res: any = await request.get('/course/list', { params })
  if (res.success) courses.value = res.data ?? []
}

const showAddForm = ref(false)
const editingId = ref<number|null>(null)
const form = ref({ name:'', weekday:1, startSlot:1, endSlot:2, weekStart:1, weekEnd:20, location:'', teacher:'', color:'#4f46e5' })

function openAddForm() {
  editingId.value = null
  form.value = { name:'', weekday:1, startSlot:1, endSlot:2, weekStart:currentWeek.value, weekEnd:totalWeeks.value, location:'', teacher:'', color:'#4f46e5' }
  showAddForm.value = true
}

function openEditForm(c: any) {
  editingId.value = c.id
  form.value = { name:c.name, weekday:c.weekday, startSlot:c.startSlot, endSlot:c.endSlot, weekStart:c.weekStart??1, weekEnd:c.weekEnd??20, location:c.location??'', teacher:c.teacher??'', color:c.color??'#4f46e5' }
  showAddForm.value = true
}

async function saveCourse() {
  if (!form.value.name) return
  if (editingId.value) {
    await request.put(`/course/${editingId.value}`, { ...form.value, semesterId: currentSemesterId.value })
  } else {
    await request.post('/course', { ...form.value, semesterId: currentSemesterId.value })
  }
  showAddForm.value = false
  await loadCourses()
}

async function doDeleteEditing() {
  if (!editingId.value) return
  await request.delete(`/course/${editingId.value}`)
  showAddForm.value = false
  await loadCourses()
}

async function deleteSemester() {
  if (!currentSemesterId.value) return
  const sem = semesters.value.find((s:any) => s.id === currentSemesterId.value)
  if (!confirm(`确认删除学期「${sem?.name}」？该学期下的课程也会一并删除。`)) return
  await request.delete(`/semester/${currentSemesterId.value}`)
  currentSemesterId.value = null
  await loadSemesters()
  await loadCourses()
}

const showAiPanel = ref(false)
const aiMode = ref<'text'|'image'>('text')
const aiInstruction = ref('')
const imageFile = ref<File|null>(null)
const aiLoading = ref(false)
const showAiConfirm = ref(false)
const parseResult = ref<any>({ ops:[], unresolved:[] })
const pendingOps = ref<any[]>([])
const aiSummary = ref('')
const confirmLoading = ref(false)

function dayLabel(val?: number) { return days.find(d=>d.val===val)?.label||'-' }
function actionLabel(a: string) { return ({CREATE:'新增',UPDATE:'修改',DELETE:'删除'} as any)[a]||a }

async function handleAiParse() {
  if (!aiInstruction.value.trim()) return
  aiLoading.value = true
  try {
    const res: any = await request.post('/course/ai-parse', { instruction: aiInstruction.value })
    if (res.success) openAiConfirm(res.data)
  } finally { aiLoading.value = false }
}

function handleFileChange(e: Event) { imageFile.value = (e.target as HTMLInputElement).files?.[0] ?? null }

async function handleImageParse() {
  if (!imageFile.value) return
  aiLoading.value = true
  try {
    const fd = new FormData()
    fd.append('file', imageFile.value)
    const res: any = await request.post('/course/import', fd, { headers:{'Content-Type':'multipart/form-data'} })
    if (res.success) openAiConfirm(res.data)
  } finally { aiLoading.value = false }
}

function openAiConfirm(data: any) {
  const ops = (data.ops ?? []).map((op: any) => {
    if (op.action === 'CREATE' && op.course && currentSemesterId.value) op.course.semesterId = currentSemesterId.value
    return op
  })
  parseResult.value = { ...data, ops }
  pendingOps.value = [...ops]
  aiSummary.value = 'AI 理解：' + (ops.length ? ops.map((op:any) =>
    `${actionLabel(op.action)} ${dayLabel(op.course?.weekday)} 第${op.course?.startSlot}-${op.course?.endSlot}节 ${op.course?.name||''}`
  ).join('；') : '未识别到操作')
  showAiConfirm.value = true
}

async function handleAiConfirm() {
  if (!pendingOps.value.length) return
  confirmLoading.value = true
  try {
    const res: any = await request.post('/course/ai-confirm', { ops: pendingOps.value })
    if (res.success) {
      showAiConfirm.value = false; showAiPanel.value = false; aiInstruction.value = ''
      await loadCourses()
      if (res.data?.slotsUpdated) { await loadSlots(); showSlotsToast() }
      if (res.data?.failed?.length) alert(`${res.data.executed}条成功，失败：${res.data.failed.join('；')}`)
    }
  } finally { confirmLoading.value = false }
}

// 课程列表
const showCourseList = ref(false)
const courseListDraft = ref<any[]>([])
const showSlotConfig = ref(false)

async function refreshCourseListDraft() {
  const params: any = {}
  if (currentSemesterId.value) params.semesterId = currentSemesterId.value
  const res: any = await request.get('/course/list', { params })
  courseListDraft.value = (res.success ? (res.data ?? []) : []).map((c: any) => ({ ...c }))
}

async function openCourseList() {
  await refreshCourseListDraft()
  showCourseList.value = true
}

async function saveRow(c: any) {
  await request.put(`/course/${c.id}`, c)
  await loadCourses()
  await refreshCourseListDraft()
}

async function deleteRow(c: any) {
  await request.delete(`/course/${c.id}`)
  await loadCourses()
  await refreshCourseListDraft()
}
const slotDraft = ref<{slot:number,start:string,end:string}[]>([])
const slotsUpdatedToast = ref(false)

function openSlotConfig() {
  slotDraft.value = SLOTS.value.map(s => ({ ...s }))
  showSlotConfig.value = true
}

function reindexSlots() {
  slotDraft.value.forEach((s, i) => { s.slot = i + 1 })
}

function addSlotRow() {
  slotDraft.value.push({ slot: slotDraft.value.length + 1, start: '', end: '' })
}

async function saveSlotConfig() {
  const slotsJson = JSON.stringify(slotDraft.value)
  const res: any = await request.put('/slot-config', { slotsJson })
  if (res.success) {
    SLOTS.value = [...slotDraft.value]
    showSlotConfig.value = false
  }
}

function showSlotsToast() {
  slotsUpdatedToast.value = true
  setTimeout(() => { slotsUpdatedToast.value = false }, 3000)
}


onMounted(async () => { await loadSlots(); await loadSemesters(); await loadCourses() })
</script>

<style scoped>
.schedule-wrap {
  padding: 72px 32px 20px;
  max-width: 100%;
  margin: 0 auto;
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(79, 70, 229, 0.1), transparent 28%),
    radial-gradient(circle at top right, rgba(8, 145, 178, 0.08), transparent 25%),
    linear-gradient(180deg, #f0f4ff 0%, #f7f8fc 50%, #eef4f7 100%);
  font-family: 'Segoe UI Variable Display', 'Microsoft YaHei UI', 'PingFang SC', sans-serif;
}

/* 顶部控制栏 */
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
  background: rgba(255,255,255,0.82);
  border: 1px solid rgba(148,163,184,0.18);
  border-radius: 20px;
  padding: 12px 18px;
  backdrop-filter: blur(8px);
  box-shadow: 0 4px 16px rgba(15,23,42,0.06);
}
.semester-area { display:flex; align-items:center; gap:8px; }
.semester-select {
  padding: 7px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 14px;
  background: #fff;
  color: #374151;
  cursor: pointer;
  outline: none;
}
.week-area { display:flex; align-items:center; gap:6px; }
.week-label { font-size:15px; font-weight:600; color:#374151; min-width:60px; text-align:center; }
.btn-week-nav {
  width: 30px; height: 30px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #374151;
  transition: background 0.15s;
}
.btn-week-nav:hover:not(:disabled) { background: #f0f4ff; }
.btn-week-nav:disabled { opacity:0.4; cursor:not-allowed; }
.action-area { display:flex; gap:8px; margin-left:auto; flex-wrap:wrap; }
.btn-sm { padding:6px 12px; border-radius:8px; font-size:13px; cursor:pointer; transition: background 0.15s; }
.btn-outline { background:#fff; border:1px solid #c7d2fe; color:#4f46e5; }
.btn-outline:hover { background:#f0f0ff; }
.btn-del-sem { background:#fff; border:1px solid #fca5a5; color:#dc2626; }
.btn-del-sem:hover { background:#fef2f2; }
.btn-add { background: linear-gradient(135deg,#4f46e5,#6366f1); color:#fff; border:none; padding:8px 16px; border-radius:10px; cursor:pointer; font-size:14px; box-shadow:0 4px 12px rgba(79,70,229,0.22); transition: opacity 0.15s, transform 0.15s; }
.btn-ai  { background:#f0f0ff; color:#4f46e5; border:1px solid #c7d2fe; padding:8px 16px; border-radius:10px; cursor:pointer; font-size:14px; transition: background 0.15s; }
.btn-slot { background:#fff; color:#374151; border:1px solid #e2e8f0; padding:8px 14px; border-radius:10px; cursor:pointer; font-size:14px; transition: background 0.15s; }
.btn-add:hover { opacity:0.88; transform:translateY(-1px); }
.btn-ai:hover  { background:#e0e7ff; }
.btn-list { background:#fff; color:#374151; border:1px solid #e5e7eb; padding:8px 14px; border-radius:10px; cursor:pointer; font-size:14px; transition: background 0.15s; }
.btn-list:hover { background:#f9fafb; }
.btn-slot:hover { background:#f9fafb; }
.modal-list { width:780px; max-width:95vw; }
.list-table-wrap { max-height:480px; overflow-y:auto; }
.list-table { border-collapse:collapse; width:100%; font-size:13px; }
.list-table th, .list-table td { border:1px solid #e5e7eb; padding:6px 8px; text-align:center; white-space:nowrap; }
.list-table th { background:#f9fafb; font-weight:600; position:sticky; top:0; z-index:1; }
.td-teacher { max-width:100px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.loc-input { width:80px; padding:2px 4px; border:1px solid #ddd; border-radius:4px; font-size:12px; }
.list-actions { display:flex; gap:4px; justify-content:center; align-items:center; }
.btn-save-row { background:#4f46e5; color:#fff; border:none; border-radius:4px; padding:3px 10px; cursor:pointer; font-size:12px; }
.btn-save-row:hover { background:#4338ca; }

/* 抽屉 */
.drawer-mask { position:fixed; inset:0; background:rgba(0,0,0,.3); z-index:200; display:flex; align-items:center; justify-content:center; padding:24px; }
.drawer { background:#fff; box-shadow:0 20px 50px rgba(0,0,0,.18); display:flex; flex-direction:column; border-radius:16px; }
.drawer-centered { width:min(980px, calc(100vw - 48px)); max-height:calc(100vh - 48px); }
.drawer-header { display:flex; align-items:center; justify-content:space-between; padding:18px 24px; border-bottom:1px solid #e5e7eb; flex-shrink:0; }
.drawer-header h3 { margin:0; font-size:16px; font-weight:600; }
.drawer-close { background:none; border:none; font-size:18px; cursor:pointer; color:#6b7280; padding:4px 8px; }
.drawer-close:hover { color:#111; }
.drawer-body { flex:1; overflow-y:auto; padding:16px 24px; }

/* AI 面板 */
.ai-panel { background:#f8f8ff; border:1px solid #e0e7ff; border-radius:10px; padding:16px; margin-bottom:16px; }
.ai-tabs  { display:flex; gap:4px; margin-bottom:12px; }
.ai-tab   { padding:6px 16px; border:1px solid #c7d2fe; border-radius:6px; background:#fff; color:#6366f1; cursor:pointer; font-size:13px; }
.ai-tab.active { background:#4f46e5; color:#fff; border-color:#4f46e5; }
.ai-input-row { display:flex; gap:8px; }
.ai-input { flex:1; padding:10px 14px; border:1px solid #c7d2fe; border-radius:8px; font-size:14px; outline:none; }
.ai-input:focus { border-color:#4f46e5; }
.ai-image-row { display:flex; gap:8px; align-items:center; }
.upload-label { flex:1; display:flex; align-items:center; padding:10px 14px; border:1.5px dashed #c7d2fe; border-radius:8px; cursor:pointer; font-size:14px; color:#6366f1; background:#fff; }
.upload-label:hover { border-color:#4f46e5; background:#f0f0ff; }
.file-input { display:none; }
.file-selected { color:#16a34a; }
.btn-parse { background:#4f46e5; color:#fff; border:none; padding:10px 20px; border-radius:8px; cursor:pointer; font-size:14px; white-space:nowrap; }
.btn-parse:disabled { opacity:0.6; cursor:not-allowed; }
.ai-tip { margin:8px 0 0; font-size:12px; color:#888; }

/* 课程表 */
.timetable { border:1px solid #e5e7eb; border-radius:10px; overflow:hidden; width:100%; }
.tt-head { display:flex; background:#f9fafb; border-bottom:2px solid #e5e7eb; }
.tt-time-th { width:88px; flex-shrink:0; padding:10px 4px; text-align:center; font-size:12px; color:#9ca3af; border-right:1px solid #e5e7eb; }
.tt-day-th { flex:1; padding:10px 4px; text-align:center; font-size:14px; font-weight:600; border-right:1px solid #e5e7eb; }
.tt-day-th:last-child { border-right:none; }
.tt-day-th.is-today { background:#eef2ff; color:#4f46e5; }

.tt-body { display:flex; }
.tt-time-col { width:88px; flex-shrink:0; border-right:1px solid #e5e7eb; }
.tt-time-cell { height:80px; display:flex; flex-direction:column; align-items:center; justify-content:center; border-bottom:1px solid #f3f4f6; box-sizing:border-box; gap:2px; }
.tt-time-cell b { font-size:15px; color:#374151; }
.tt-time-cell span { font-size:11px; color:#9ca3af; line-height:1.4; }

.tt-day-col { flex:1; border-right:1px solid #e5e7eb; }
.tt-day-col:last-child { border-right:none; }
.tt-day-col.is-future { background:#fafafa; }
.tt-slot-cell { height:80px; border-bottom:1px solid #f3f4f6; box-sizing:border-box; position:relative; }
.tt-slot-cell:last-child { border-bottom:none; }

/* 课程块：绝对定位在 startSlot 的格子里，高度撑开覆盖多节 */
.course-block {
  border-radius:6px; color:#fff;
  padding:5px 7px; cursor:pointer;
  display:flex; flex-direction:column; gap:2px;
  overflow:hidden; box-sizing:border-box;
  transition:opacity .15s;
}
.course-block:hover { opacity:0.82; }
.tt-day-col.is-future .course-block { opacity:0.4; }
.cb-name   { font-size:12px; font-weight:700; line-height:1.3; }
.cb-sub    { font-size:11px; opacity:0.9; }
.cb-week   { font-size:10px; opacity:0.7; margin-top:auto; }

/* 弹窗 */
.modal-mask { position:fixed; inset:0; background:rgba(0,0,0,.45); display:flex; align-items:center; justify-content:center; z-index:200; }
.modal { background:#fff; border-radius:14px; padding:28px; width:460px; max-width:95vw; display:flex; flex-direction:column; gap:10px; max-height:90vh; overflow-y:auto; }
.modal-wide { width:760px; }
.modal-sm   { width:300px; }
.modal h3   { margin:0 0 4px; font-size:17px; }
.form-grid  { display:grid; grid-template-columns:80px 1fr; gap:8px; align-items:center; }
.form-grid label { font-size:13px; color:#555; text-align:right; }
.form-grid input, .form-grid select { padding:8px 10px; border:1px solid #ddd; border-radius:6px; font-size:14px; }
.modal-btns { display:flex; gap:8px; margin-top:4px; }
.btn-confirm { flex:1; padding:10px; border:none; border-radius:8px; cursor:pointer; background:#4f46e5; color:#fff; font-size:14px; }
.btn-confirm:disabled { opacity:.5; cursor:not-allowed; }
.btn-cancel  { flex:1; padding:10px; border:none; border-radius:8px; cursor:pointer; background:#f3f4f6; color:#333; font-size:14px; }
.btn-danger  { flex:1; padding:10px; border:none; border-radius:8px; cursor:pointer; background:#ef4444; color:#fff; font-size:14px; }

.ai-summary    { font-size:14px; color:#4f46e5; background:#f0f0ff; border-radius:8px; padding:10px 14px; margin:0; }
.unresolved-box { background:#fff7ed; border:1px solid #fed7aa; border-radius:8px; padding:10px 14px; font-size:13px; }
.unresolved-label { color:#ea580c; font-weight:600; }
.unresolved-item  { color:#7c3aed; margin-left:6px; }
.preview-table-wrap { overflow-x:auto; }
.preview-table { border-collapse:collapse; width:100%; font-size:13px; }
.preview-table th, .preview-table td { border:1px solid #e5e7eb; padding:7px 10px; text-align:center; }
.preview-table th { background:#f9fafb; font-weight:600; }
.op-badge { display:inline-block; padding:2px 8px; border-radius:4px; font-size:12px; font-weight:600; }
.op-badge.create { background:#dcfce7; color:#16a34a; }
.op-badge.update { background:#fef9c3; color:#ca8a04; }
.op-badge.delete { background:#fee2e2; color:#dc2626; }
.btn-remove-op { background:none; border:none; color:#aaa; cursor:pointer; font-size:14px; padding:2px 6px; }
.btn-remove-op:hover { color:#ef4444; }
.week-input { width:44px; padding:2px 4px; border:1px solid #ddd; border-radius:4px; font-size:12px; text-align:center; }
.preview-hint { font-size:12px; color:#aaa; margin:6px 0 0; }
.empty-ops    { text-align:center; color:#aaa; padding:20px; font-size:14px; }
.delete-info  { font-size:14px; color:#555; line-height:1.8; margin:0; }

/* 节次配置弹窗 */
.modal-slot { width:340px; }
.slot-table-wrap { max-height:400px; overflow-y:auto; }
.slot-table { border-collapse:collapse; width:100%; font-size:13px; }
.slot-table th, .slot-table td { border:1px solid #e5e7eb; padding:6px 8px; text-align:center; }
.slot-table th { background:#f9fafb; font-weight:600; }
.time-input { width:70px; padding:4px 6px; border:1px solid #ddd; border-radius:4px; font-size:13px; text-align:center; }
.btn-add-slot { background:none; border:1px dashed #c7d2fe; color:#6366f1; border-radius:6px; padding:6px 14px; cursor:pointer; font-size:13px; width:100%; margin-top:4px; }
.btn-add-slot:hover { background:#f0f0ff; }

/* toast */
.slots-toast { position:fixed; bottom:32px; left:50%; transform:translateX(-50%); background:#16a34a; color:#fff; padding:10px 24px; border-radius:8px; font-size:14px; z-index:999; box-shadow:0 4px 12px rgba(0,0,0,.15); }
</style>
