<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppShell from './components/AppShell.vue'

const route = useRoute()
const hideShell = computed(() => route.meta.hideShell === true)
const keepAliveInclude = ['FeedView', 'ProfileView']
</script>

<template>
  <RouterView v-if="hideShell" />
  <AppShell v-else>
    <RouterView v-slot="{ Component, route: currentRoute }">
      <Transition name="app-page" mode="out-in">
        <KeepAlive :include="keepAliveInclude">
          <component :is="Component" :key="currentRoute.name === 'feed' || currentRoute.name === 'profile' || currentRoute.name === 'user-profile' ? currentRoute.name : currentRoute.fullPath" />
        </KeepAlive>
      </Transition>
    </RouterView>
  </AppShell>
</template>
