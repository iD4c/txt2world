<template>
  <div class="app-root">
    <router-view/>
    <TopScope></TopScope>
  </div>
</template>

<script setup>
import TopScope from "@/pages/layouts/TopScope.vue";
</script>

<style>
html {
  height: 100%;
  margin: 0;
  overflow: hidden;
}

body,
#app {
  height: 100%;
  margin: 0;
  overflow: hidden;
}

* {
  box-sizing: border-box;
}

body {
  color: #251f17;
  font-family: "KaiTi", "STKaiti", "Microsoft YaHei", sans-serif;
}

button {
  font: inherit;
}

.app-root {
  height: 100%;
  overflow: hidden;
}
</style>
