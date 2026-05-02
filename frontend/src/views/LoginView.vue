<template>
  <div class="login-wrap">
    <div class="login-card">
      <div class="brand">
        <span class="brand-icon">✦</span>
        <span class="brand-name">Personal Context</span>
      </div>
      <h1>欢迎回来</h1>
      <p class="sub">课程表 · 备忘录 · 智能提醒</p>
      <p class="default-hint">默认账号：13800000000 / admin123</p>

      <div class="field-group">
        <label>手机号</label>
        <input v-model="phone" maxlength="11" placeholder="13800000000" @keydown.enter="login" />
      </div>
      <div class="field-group">
        <label>验证码</label>
        <input v-model="code" placeholder="admin123" @keydown.enter="login" />
      </div>

      <button class="login-btn" :disabled="loading" type="button" @click="login">
        {{ loading ? '登录中...' : '登录' }}
      </button>

      <p v-if="msg" class="tip">{{ msg }}</p>
    </div>

    <div class="bg-deco deco1" />
    <div class="bg-deco deco2" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'

const phone = ref(localStorage.getItem('saved_phone') || '13800000000')
const code = ref(localStorage.getItem('saved_code') || 'admin123')
const msg = ref('')
const loading = ref(false)
const router = useRouter()

async function login() {
  if (!phone.value || !code.value) {
    msg.value = '请填写完整信息'
    return
  }
  loading.value = true
  msg.value = ''
  try {
    const res: any = await request.post('/user/login', { phone: phone.value, code: code.value })
    if (res.success) {
      localStorage.setItem('token', res.data)
      localStorage.setItem('saved_phone', phone.value)
      localStorage.setItem('saved_code', code.value)
      router.push('/')
      return
    }
    msg.value = res.errorMsg || '登录失败'
  } catch {
    msg.value = '登录失败，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background:
    radial-gradient(circle at 20% 20%, rgba(79, 70, 229, 0.14), transparent 40%),
    radial-gradient(circle at 80% 80%, rgba(8, 145, 178, 0.12), transparent 40%),
    linear-gradient(160deg, #f0f4ff 0%, #f8f9fc 50%, #eef6f8 100%);
  overflow: hidden;
  position: relative;
}

.bg-deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}
.deco1 {
  width: 480px; height: 480px;
  top: -160px; left: -160px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.08), transparent 70%);
}
.deco2 {
  width: 360px; height: 360px;
  bottom: -120px; right: -80px;
  background: radial-gradient(circle, rgba(8, 145, 178, 0.08), transparent 70%);
}

.login-card {
  position: relative;
  z-index: 1;
  width: 400px;
  padding: 44px 40px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.18);
  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.1);
  backdrop-filter: blur(12px);
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 28px;
}
.brand-icon {
  font-size: 20px;
  color: #4f46e5;
}
.brand-name {
  font-size: 13px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #6366f1;
  font-weight: 600;
}

h1 {
  font-size: 32px;
  color: #1e293b;
  margin-bottom: 6px;
  line-height: 1.1;
}

.sub {
  font-size: 14px;
  color: #64748b;
  margin-bottom: 12px;
}

.default-hint {
  font-size: 13px;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.08);
  padding: 8px 12px;
  border-radius: 10px;
  margin-bottom: 24px;
  text-align: center;
  font-weight: 500;
}

.field-group {
  margin-bottom: 18px;
}
.field-group label {
  display: block;
  font-size: 13px;
  color: #475569;
  margin-bottom: 6px;
  font-weight: 500;
}
.field-group input {
  width: 100%;
  padding: 13px 16px;
  border: 1.5px solid #e2e8f0;
  border-radius: 14px;
  font-size: 15px;
  outline: none;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.9);
  color: #1e293b;
  transition: border-color 0.18s;
}
.field-group input:focus {
  border-color: #6366f1;
}

.login-btn {
  width: 100%;
  padding: 14px;
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  border: none;
  border-radius: 14px;
  cursor: pointer;
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  box-shadow: 0 8px 24px rgba(79, 70, 229, 0.28);
  transition: opacity 0.18s, transform 0.18s;
  margin-top: 8px;
}
.login-btn:hover:not(:disabled) {
  opacity: 0.9;
  transform: translateY(-1px);
}
.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.tip {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: #dc2626;
  background: #fef2f2;
  border-radius: 10px;
  padding: 8px 12px;
}
</style>
