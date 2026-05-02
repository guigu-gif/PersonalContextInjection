import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export type Profile = {
  identity: 'student' | 'elder' | 'general'
  fontSize: 'normal' | 'large' | 'xlarge'
  theme: 'default' | 'elder' | 'dark'
  wallpaper: string
}

export const useProfileStore = defineStore('profile', () => {
  const profile = ref<Profile>({
    identity: 'general',
    fontSize: 'normal',
    theme: 'default',
    wallpaper: 'none',
  })

  function applyToDOM(p: Profile) {
    const root = document.documentElement
    root.setAttribute('data-theme', p.theme)
    root.setAttribute('data-font', p.fontSize)
    root.setAttribute('data-wallpaper', p.wallpaper)
  }

  async function load() {
    if (!localStorage.getItem('token')) return
    try {
      const res: any = await request.get('/user/profile')
      if (res.success && res.data) {
        profile.value = res.data
        applyToDOM(res.data)
      }
    } catch { /* 静默失败，用默认值 */ }
  }

  async function applyAction(action: string, value: string) {
    const res: any = await request.put('/user/profile/action', { action, value })
    if (res.success && res.data) {
      profile.value = res.data
      applyToDOM(res.data)
    }
    return res
  }

  return { profile, load, applyAction }
})
