<script setup lang="ts">
import { RouterView } from 'vue-router'
import PublicHeader from '@/components/PublicHeader.vue'
import UserSidebar from '@/components/UserSidebar.vue'
</script>

<template>
  <div class="app-frame">
    <PublicHeader />
    <main class="workspace-shell">
      <UserSidebar />
      <section class="workspace-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>
