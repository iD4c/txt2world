import {ref} from "vue";
import {defineStore} from "pinia";
import {ROUTER_NAMES} from "@/router.js";
import {Stack} from "@/global/commonFun.js";

export const useUiStore = defineStore('ui', () => {

  const shared_showNavDrawer = ref(true);
  const shared_showAppBar = ref(true);

  return {shared_showNavDrawer, shared_showAppBar};
});

export const usePageSwitchStore = defineStore('pageSwitch', () => {

  const shared_pageNameHist = ref(new Stack([ROUTER_NAMES.WIKI_VIEW_CHOOSE_PROJECT]));
  const shared_transitionName = ref('');

  return {shared_pageNameHist, shared_transitionName};
});

export const usePageDataStore = defineStore('pageData', () => {

  const shared_pageDataStore = ref(new Map());

  return {shared_pageDataStore};
});
