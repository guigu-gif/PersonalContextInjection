import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { useProfileStore } from '@/stores/profile'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

router.afterEach((to) => {
  if (to.path !== '/login' && localStorage.getItem('token')) {
    useProfileStore().load()
  }
})

app.mount('#app')
