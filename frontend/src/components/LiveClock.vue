<template>
  <div class="live-clock">
    <span class="clock-date">{{ dateStr }}</span>
    <span class="clock-lunar">{{ lunarStr }}</span>
    <span class="clock-time">{{ timeStr }}</span>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { toLunar } from '@/utils/lunar'

const dateStr  = ref('')
const lunarStr = ref('')
const timeStr  = ref('')

function pad(n: number) { return String(n).padStart(2, '0') }

function tick() {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  const d = now.getDate()
  const weekMap = ['日','一','二','三','四','五','六']
  dateStr.value  = `${y}年${m}月${d}日 周${weekMap[now.getDay()]}`
  lunarStr.value = toLunar(now)
  timeStr.value  = `${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())}`
}

let timer: ReturnType<typeof setInterval>
onMounted(() => { tick(); timer = setInterval(tick, 1000) })
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.live-clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex-shrink: 0;
}
.clock-date {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}
.clock-lunar {
  font-size: 12px;
  color: #94a3b8;
}
.clock-time {
  font-size: 22px;
  font-weight: 600;
  color: #1e293b;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.04em;
}
</style>
