<template>
  <div class="wiki-shell">
    <AppDrawer/>
    <section class="wiki-main-area">
      <AppBar/>
      <main class="wiki-main-content">
        <transition :name="shared_transitionName" mode="out-in">
          <component :is="currentComponent"/>
        </transition>
      </main>
    </section>
  </div>
</template>

<script setup>
import {computed} from "vue";
import {storeToRefs} from "pinia";
import {getRouterComponent} from "@/global/commonFun.js";
import {usePageSwitchStore} from "@/store.js";
import AppDrawer from "@/pages/layouts/AppDrawer.vue";
import AppBar from "@/pages/layouts/AppBar.vue";

const pageSwitchStore = usePageSwitchStore();
const {shared_pageNameHist, shared_transitionName} = storeToRefs(pageSwitchStore);

const currentComponent = computed(() => {
  const peek = shared_pageNameHist.value.peek();
  return getRouterComponent(peek);
});
</script>

<style scoped lang="scss">
.wiki-shell {
  display: flex;
  width: 100%;
  height: 100vh;
  overflow: hidden;
  background: #f7f0df;
}

.wiki-main-area {
  position: relative;
  flex: 1 1 auto;
  min-width: 0;
  height: 100%;
  overflow: hidden;
  background: #f7f0df;
  isolation: isolate;
}

.wiki-main-area::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: url('/main-bg.png') center top / cover repeat;
  filter: brightness(1.08) contrast(0.86) saturate(0.82);
  opacity: 0.58;
}

.wiki-main-area::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(180deg, rgba(255, 250, 238, 0.26), rgba(247, 240, 223, 0.38));
}

.wiki-main-content {
  position: relative;
  z-index: 1;
  height: calc(100% - 76px);
  overflow-x: hidden;
  overflow-y: auto;
  scrollbar-color: rgba(129, 95, 43, 0.44) rgba(255, 249, 236, 0.34);
  scrollbar-width: thin;
}

.wiki-main-content::-webkit-scrollbar {
  width: 10px;
}

.wiki-main-content::-webkit-scrollbar-track {
  background: rgba(255, 249, 236, 0.34);
  border-left: 1px solid rgba(88, 67, 38, 0.08);
}

.wiki-main-content::-webkit-scrollbar-thumb {
  border: 2px solid rgba(255, 249, 236, 0.72);
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(169, 128, 50, 0.68), rgba(91, 69, 40, 0.42));
}

.wiki-main-content::-webkit-scrollbar-thumb:hover {
  background: linear-gradient(180deg, rgba(169, 128, 50, 0.82), rgba(91, 69, 40, 0.58));
}
</style>
