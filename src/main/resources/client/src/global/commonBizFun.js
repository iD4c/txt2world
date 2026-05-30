import {usePageDataStore, usePageSwitchStore, useUiStore} from "@/store.js";
import {storeToRefs} from "pinia";

export function goNextPage(name) {
  const pageSwitchStore = usePageSwitchStore();
  const {shared_pageNameHist, shared_transitionName} = storeToRefs(pageSwitchStore);
  shared_transitionName.value = 'slide-left';
  shared_pageNameHist.value.push(name);
}

export function goNextPageFullScreen(name) {
  goNextPage(name);
  const uiStore = useUiStore();
  const {shared_showNavDrawer, shared_showAppBar} = storeToRefs(uiStore);
  shared_showNavDrawer.value = false;
  shared_showAppBar.value = false;
}

export function back() {
  const pageSwitchStore = usePageSwitchStore();
  const {shared_pageNameHist, shared_transitionName} = storeToRefs(pageSwitchStore);
  shared_transitionName.value = 'slide-right';
  shared_pageNameHist.value.pop();
}

export function backExitFullScreen() {
  back();
  const uiStore = useUiStore();
  const {shared_showNavDrawer, shared_showAppBar} = storeToRefs(uiStore);
  shared_showNavDrawer.value = true;
  shared_showAppBar.value = true;
}

export function sendPageData(dist, pageData) {
  const pageDataStore = usePageDataStore();
  const {shared_pageDataStore} = storeToRefs(pageDataStore);
  shared_pageDataStore.value.set(dist, pageData);
}

export function getPageData() {
  const pageDataStore = usePageDataStore();
  const {shared_pageDataStore} = storeToRefs(pageDataStore);
  const currentPageName = getCurrentPageName();
  let pageData = shared_pageDataStore.value.get(currentPageName);
  if (!pageData) {
    pageData = {};
    shared_pageDataStore.value.set(currentPageName, pageData);
  }

  console.log(`getPageData ${currentPageName.toString()}: `, pageData);
  return pageData;
}

export function getCurrentPageName() {
  const pageSwitchStore = usePageSwitchStore();
  const {shared_pageNameHist} = storeToRefs(pageSwitchStore);
  return shared_pageNameHist.value.peek();
}

export function getParentPageName() {
  const pageSwitchStore = usePageSwitchStore();
  const {shared_pageNameHist} = storeToRefs(pageSwitchStore);
  return shared_pageNameHist.value.getSecond();
}
