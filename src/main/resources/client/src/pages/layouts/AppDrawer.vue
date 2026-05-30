<template>
  <aside v-if="shared_showNavDrawer" class="app-drawer">
    <div class="app-drawer__brand">
      <div class="app-drawer__brand-mark">
        <i class="mdi mdi-compass-outline"></i>
      </div>
      <div>
        <div class="app-drawer__brand-title">小说百科</div>
        <div class="app-drawer__brand-subtitle">TXT TO WORLD</div>
      </div>
    </div>

    <nav class="app-drawer__nav" aria-label="主导航">
      <button
          class="app-drawer__section"
          :class="{'app-drawer__section--active': isProjectManageActive}"
          type="button"
          @click="jumpPage(ROUTER_NAMES.WIKI_PROJECT_MANAGE)"
      >
        <i class="app-drawer__section-icon mdi mdi-notebook-outline"></i>
        <span>Wiki 项目管理</span>
      </button>

      <button
          class="app-drawer__nav-item"
          :class="{'app-drawer__nav-item--active': isBrowseWikiActive}"
          type="button"
          @click="jumpPage(ROUTER_NAMES.WIKI_VIEW_CHOOSE_PROJECT)"
      >
        <i class="app-drawer__nav-icon mdi mdi-compass-outline"></i>
        <span>浏览 Wiki</span>
      </button>
    </nav>
  </aside>
</template>

<script setup>
import {computed} from "vue";
import {storeToRefs} from "pinia";
import {ROUTER_NAMES} from "@/router.js";
import {usePageSwitchStore, useUiStore} from "@/store.js";

const uiStore = useUiStore();
const pageSwitchStore = usePageSwitchStore();
const {shared_showNavDrawer} = storeToRefs(uiStore);
const {shared_pageNameHist, shared_transitionName} = storeToRefs(pageSwitchStore);

const activeComponentName = computed(() => shared_pageNameHist.value.peek());
const isProjectManageActive = computed(() => {
  return activeComponentName.value === ROUTER_NAMES.WIKI_PROJECT_MANAGE
      || activeComponentName.value === ROUTER_NAMES.WIKI_PROJECT_UPLOAD;
});
const isBrowseWikiActive = computed(() => {
  return activeComponentName.value === ROUTER_NAMES.WIKI_VIEW_CHOOSE_PROJECT
      || activeComponentName.value === ROUTER_NAMES.HOME
      || activeComponentName.value === ROUTER_NAMES.WIKI_VIEW;
});

function jumpPage(name) {
  shared_transitionName.value = '';
  shared_pageNameHist.value.setOne(name);
}
</script>

<style scoped lang="scss">
.app-drawer {
  position: relative;
  z-index: 3;
  flex: 0 0 260px;
  width: 260px;
  height: 100vh;
  isolation: isolate;
  overflow: hidden;
  border-right: 1px solid rgba(88, 67, 38, 0.16);
  background-color: #faf4e6;
  box-shadow: 10px 0 28px rgba(75, 58, 35, 0.05);
  color: #251f17;
}

.app-drawer::before {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: url('/navi-bg.png') center bottom / auto 100% no-repeat;
  filter: brightness(1.06) contrast(0.94) saturate(0.9);
  opacity: 0.72;
}

.app-drawer::after {
  content: "";
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background: linear-gradient(
    180deg,
    rgba(255, 251, 241, 0.78) 0%,
    rgba(250, 244, 229, 0.46) 42%,
    rgba(244, 232, 210, 0.16) 100%
  );
}

.app-drawer__brand,
.app-drawer__nav {
  position: relative;
  z-index: 1;
}

.app-drawer__brand {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 108px;
  padding: 30px 22px 0;
  margin-bottom: 42px;
}

.app-drawer__brand-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 2px solid rgba(37, 31, 23, 0.82);
  border-radius: 50%;
  font-size: 28px;
  line-height: 1;
}

.app-drawer__brand-title {
  font-size: 30px;
  font-weight: 700;
  line-height: 1.15;
  white-space: nowrap;
}

.app-drawer__brand-subtitle {
  margin-top: 6px;
  font-family: Georgia, serif;
  font-size: 13px;
  color: rgba(37, 31, 23, 0.76);
  white-space: nowrap;
}

.app-drawer__nav {
  padding: 0 22px;
}

.app-drawer__section {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 48px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: rgba(255, 250, 240, 0);
  color: #393125;
  font-size: 16px;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
}

.app-drawer__section--active {
  border-color: rgba(130, 99, 53, 0.3);
  background: rgba(231, 207, 162, 0.34);
  box-shadow: inset 0 0 0 1px rgba(255, 252, 244, 0.58), 0 10px 22px rgba(94, 70, 36, 0.06);
  color: #251f17;
  font-weight: 700;
}

.app-drawer__section-icon,
.app-drawer__nav-icon {
  width: 24px;
  text-align: center;
  font-size: 24px;
  line-height: 1;
}

.app-drawer__nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 56px;
  margin-top: 18px;
  padding: 0 12px;
  background: rgba(255, 250, 240, 0.24);
  color: #251f17;
  font-size: 18px;
  font-weight: 700;
  font-family: inherit;
  text-decoration: none;
  text-align: left;
  cursor: pointer;
}

.app-drawer__nav-item--active {
  //border-color: rgba(130, 99, 53, 0.3);
  background: rgba(231, 207, 162, 0.34);
  box-shadow: inset 0 0 0 1px rgba(255, 252, 244, 0.58), 0 10px 22px rgba(94, 70, 36, 0.06);
  border: 1px solid rgba(130, 99, 53, 0.3);
  border-radius: 8px;
}
</style>
