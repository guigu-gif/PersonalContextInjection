<template>
  <div class="app-menu">
    <button class="menu-trigger" type="button" @click="open = true">☰ 功能</button>

    <transition name="menu-fade">
      <div v-if="open" class="menu-mask" @click="open = false" />
    </transition>

    <aside :class="['menu-panel', open ? 'open' : '']">
      <div class="panel-header">
        <div>
          <p class="panel-title">功能导航</p>
          <h2>Personal Context Injection</h2>
        </div>
        <button class="icon-btn" type="button" @click="open = false">收起</button>
      </div>

      <nav class="side-nav">
        <router-link to="/home" @click="open = false">首页</router-link>
        <router-link to="/schedule" @click="open = false">课程表</router-link>
        <router-link to="/memo" @click="open = false">备忘录</router-link>
        <router-link to="/notify" @click="open = false">通知中心</router-link>
        <router-link to="/travel" @click="open = false">出行规划</router-link>
        <router-link to="/guide" @click="open = false">攻略中心</router-link>
        <router-link to="/chat" @click="open = false">✦ AI 助手</router-link>
        <router-link to="/settings" @click="open = false">设置</router-link>
      </nav>

      <div class="panel-foot">
        <button class="logout-btn" type="button" @click="logout">退出登录</button>
      </div>
    </aside>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const open = ref(false)
const router = useRouter()

function logout() {
  localStorage.removeItem('token')
  router.push('/login')
}
</script>

<style scoped>
.menu-trigger {
  position: fixed;
  top: 16px;
  left: 16px;
  z-index: 45;
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.92);
  color: #233241;
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.14);
}

.menu-mask {
  position: fixed;
  inset: 0;
  z-index: 39;
  background: rgba(15, 23, 42, 0.28);
}

.menu-panel {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: 280px;
  padding: 22px 18px;
  overflow-y: auto;
  transform: translateX(-100%);
  transition: transform 0.24s ease;
  background: rgba(18, 28, 40, 0.96);
  color: #f5f7fa;
  z-index: 40;
  box-shadow: 18px 0 50px rgba(15, 23, 42, 0.2);
}

.menu-panel.open {
  transform: translateX(0);
}

.panel-header,
.side-nav {
  display: grid;
  gap: 10px;
}

.panel-header {
  margin-bottom: 28px;
}

.panel-title {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: rgba(245, 247, 250, 0.6);
}

.panel-header h2 {
  font-size: 22px;
  margin-top: 4px;
}

.icon-btn {
  justify-self: end;
  border: none;
  border-radius: 999px;
  padding: 10px 14px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.9);
  color: #233241;
}

.side-nav a {
  color: #f5f7fa;
  text-decoration: none;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.05);
}

.side-nav a.router-link-active {
  background: rgba(255, 255, 255, 0.14);
}

.nav-disabled {
  color: rgba(245, 247, 250, 0.35);
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.02);
  font-size: inherit;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: default;
}

.coming-soon {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(245, 247, 250, 0.4);
}

.panel-foot {
  margin-top: 28px;
  border-top: 1px solid rgba(255, 255, 255, 0.12);
  padding-top: 16px;
}

.logout-btn {
  width: 100%;
  padding: 11px 14px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.07);
  color: rgba(245, 247, 250, 0.8);
  font-size: 14px;
  cursor: pointer;
  text-align: left;
  transition: background 0.18s;
}
.logout-btn:hover {
  background: rgba(239, 68, 68, 0.18);
  color: #fca5a5;
  border-color: rgba(239, 68, 68, 0.25);
}

.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: opacity 0.2s ease;
}

.menu-fade-enter-from,
.menu-fade-leave-to {
  opacity: 0;
}
</style>
