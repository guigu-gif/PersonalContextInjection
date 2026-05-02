<template>
  <RouterView />
  <FloatAI v-if="isLoggedIn" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import FloatAI from '@/components/FloatAI.vue'

const route = useRoute()
const isLoggedIn = computed(() => route.path !== '/login' && !!localStorage.getItem('token'))
</script>
