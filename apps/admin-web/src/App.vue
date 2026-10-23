<template>
  <div class="nav-bar" :class="{ on: navState.pending }"><i /></div>
  <router-view v-slot="{ Component }">
    <component :is="Component" />
  </router-view>
</template>

<script setup lang="ts">
import { navState } from '@/router'
</script>

<style scoped>
.nav-bar {
  position: fixed; top: 0; left: 0; right: 0; height: 3px;
  z-index: 3000; pointer-events: none;
  opacity: 0; transition: opacity .2s;
}
.nav-bar.on { opacity: 1; }
.nav-bar i {
  display: block; height: 100%; width: 40%;
  background: linear-gradient(90deg, #5b8cff, #3d6ef5, #7aa2ff);
  border-radius: 0 3px 3px 0;
  box-shadow: 0 0 8px rgba(61, 110, 245, .5);
}
.nav-bar.on i { animation: slide 1.1s ease-in-out infinite; }
@keyframes slide {
  0% { transform: translateX(-100%); }
  60% { transform: translateX(180%); }
  100% { transform: translateX(260%); }
}
</style>
