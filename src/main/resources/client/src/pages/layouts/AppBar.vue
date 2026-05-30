<template>
  <header v-if="shared_showAppBar" class="app-bar">
    <div class="app-bar__left">
      <button class="app-bar__icon-button" type="button" @click="toggleDrawer" aria-label="切换导航栏">
        <i class="mdi mdi-menu"></i>
      </button>

      <v-btn v-if="canGoBack" class="app-bar__back-button" type="button" variant="flat" elevation="0" @click="goBack">
        <v-icon icon="mdi-arrow-left"></v-icon>
        <span>返回</span>
      </v-btn>
    </div>

    <nav class="app-bar__breadcrumbs" aria-label="页面路径">
      <template v-for="(item, index) in breadcrumbItems" :key="item">
        <span
            class="app-bar__breadcrumb"
            :class="{'app-bar__breadcrumb--current': index === breadcrumbItems.length - 1}"
        >
          {{ item }}
        </span>
        <span v-if="index < breadcrumbItems.length - 1" class="app-bar__separator">/</span>
      </template>
    </nav>
  </header>
</template>

<script setup>
import {computed} from "vue";
import {storeToRefs} from "pinia";
import {getRouterMeta} from "@/global/commonFun.js";
import {ROUTER_NAMES} from "@/router.js";
import {usePageDataStore, usePageSwitchStore, useUiStore} from "@/store.js";

const uiStore = useUiStore();
const pageSwitchStore = usePageSwitchStore();
const pageDataStore = usePageDataStore();
const {shared_showAppBar, shared_showNavDrawer} = storeToRefs(uiStore);
const {shared_pageNameHist} = storeToRefs(pageSwitchStore);
const {shared_pageDataStore} = storeToRefs(pageDataStore);

const breadcrumbItems = computed(() => {
  const currentName = shared_pageNameHist.value.peek();
  const selectedProject = shared_pageDataStore.value.get('selectedWikiProject')
      || shared_pageDataStore.value.get(ROUTER_NAMES.WIKI_VIEW)?.wikiProject;
  if ((currentName === ROUTER_NAMES.HOME || currentName === ROUTER_NAMES.WIKI_VIEW) && selectedProject?.name) {
    return ['选择项目', selectedProject.name, '关系图'];
  }
  return getRouterMeta(currentName)?.breadcrumbs || ['首页'];
});

const canGoBack = computed(() => shared_pageNameHist.value.size() > 1);

function toggleDrawer() {
  shared_showNavDrawer.value = !shared_showNavDrawer.value;
}

function goBack() {
  if (!canGoBack.value) {
    return;
  }
  shared_pageNameHist.value.pop();
}
</script>

<style scoped lang="scss">
.app-bar {
  position: relative;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  height: 76px;
  padding: 0 36px;
  border-bottom: 1px solid rgba(73, 57, 35, 0.12);
  background: rgba(250, 244, 229, 0.45);
  backdrop-filter: blur(8px);
  color: #251f17;
}

.app-bar__left {
  display: flex;
  align-items: center;
  gap: 16px;
  flex: 0 0 auto;
}

.app-bar__icon-button,
.app-bar__back-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  border: 1px solid rgba(97, 75, 45, 0.24);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.66);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.44), 0 8px 20px rgba(78, 58, 31, 0.06);
  color: #2c261d;
  cursor: pointer;
}

.app-bar__icon-button {
  width: 48px;
}

.app-bar__icon-button .mdi,
.app-bar__back-button .mdi {
  font-size: 24px;
  line-height: 1;
}

.app-bar__back-button {
  gap: 10px;
  min-width: 106px;
  padding: 0 18px;
  font-size: 16px;
  letter-spacing: 0;
  text-transform: none;
}

.app-bar__breadcrumbs {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  margin-left: 16px;
  min-width: 0;
  overflow: hidden;
  color: #2d261b;
  white-space: nowrap;
}

.app-bar__breadcrumb,
.app-bar__separator {
  flex: 0 0 auto;
  font-size: 17px;
}

.app-bar__breadcrumb--current {
  font-weight: 700;
}

.app-bar__separator {
  margin: 0 18px;
  color: rgba(56, 45, 31, 0.55);
}

@media (max-width: 720px) {
  .app-bar {
    padding: 0 16px;
    gap: 14px;
  }

  .app-bar__left {
    gap: 10px;
  }

  .app-bar__back-button {
    min-width: 84px;
    padding: 0 12px;
  }

  .app-bar__breadcrumb,
  .app-bar__separator {
    font-size: 14px;
  }

  .app-bar__separator {
    margin: 0 8px;
  }
}
</style>
