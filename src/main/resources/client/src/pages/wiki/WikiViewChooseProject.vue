<template>
  <section class="wiki-choose-page">
    <div class="choose-hero">
      <div>
        <p class="choose-hero__eyebrow">BROWSE WIKI</p>
        <h1>选择 Wiki 项目</h1>
        <p>从已上传的小说 Wiki 项目中选择一个，进入关系图、人物、物品与势力视图。</p>
      </div>
      <button class="paper-button" type="button" :disabled="loading" @click="loadProjects(true)">
        <i class="mdi mdi-refresh"></i>
        <span>刷新</span>
      </button>
    </div>

    <div v-if="errorText" class="paper-alert">
      {{ errorText }}
    </div>

    <div v-if="hasProjects" class="choose-grid">
      <article
        v-for="project in projects"
        :key="project.id || project.name"
        class="choose-card"
        :class="{'choose-card--selected': selectedProjectId === project.id}"
        @click="chooseProject(project)"
      >
        <div class="choose-card__header">
          <div class="choose-card__seal">
            <i class="mdi mdi-compass-rose"></i>
          </div>
          <span class="choose-card__status" :class="statusClass(project.parseStatus)">
            {{ project.parseStatus || '未知状态' }}
          </span>
        </div>

        <h2>{{ project.name || '未命名项目' }}</h2>
        <p class="choose-card__desc">
          共 {{ project.chapterNum ?? '-' }} 章，按 {{ project.chunkSize ?? '-' }} 章切片生成 Wiki 档案。
        </p>

        <div class="choose-card__footer">
          <span>{{ formatDate(project.createDate) }}</span>
          <button class="enter-button" type="button" :disabled="entering" @click.stop="enterWiki(project)">
            <span>{{ entering && selectedProjectId === project.id ? '载入中' : '进入 Wiki' }}</span>
            <i class="mdi mdi-arrow-right"></i>
          </button>
        </div>
      </article>
    </div>

    <div v-else-if="!loading" class="empty-state">
      <i class="mdi mdi-bookshelf"></i>
      <p>还没有可浏览的 Wiki 项目</p>
    </div>

    <div ref="sentinelRef" class="load-sentinel">
      <span v-if="loading">正在载入项目...</span>
      <span v-else-if="finished && hasProjects">已加载全部项目</span>
    </div>
  </section>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue";
import {axiosPostSilent} from "@/api/axios.js";
import {goNextPage, sendPageData} from "@/global/commonBizFun.js";
import {ROUTER_NAMES} from "@/router.js";

const projects = ref([]);
const loading = ref(false);
const entering = ref(false);
const finished = ref(false);
const errorText = ref('');
const selectedProjectId = ref('');
const pageNo = ref(1);
const pageSize = 12;
const sentinelRef = ref(null);

let observer;

const hasProjects = computed(() => projects.value.length > 0);
const selectedProject = computed(() => {
  return projects.value.find(project => project.id === selectedProjectId.value) || null;
});

function initInfiniteObserver() {
  if (!sentinelRef.value) {
    return;
  }

  const root = document.querySelector('.wiki-main-content');
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting) {
      loadProjects();
    }
  }, {
    root,
    rootMargin: '260px 0px 260px 0px'
  });
  observer.observe(sentinelRef.value);
}

function normalizeProjectList(data) {
  if (Array.isArray(data)) {
    return data;
  }
  if (Array.isArray(data?.content)) {
    return data.content;
  }
  if (Array.isArray(data?.records)) {
    return data.records;
  }
  if (Array.isArray(data?.list)) {
    return data.list;
  }
  return [];
}

function chooseProject(project) {
  selectedProjectId.value = project.id;
}

function statusClass(status) {
  if (status === '已解析') {
    return 'is-success';
  }
  if (status === '解析失败') {
    return 'is-error';
  }
  return 'is-running';
}

function formatDate(value) {
  return value || '暂无时间';
}

async function loadProjects(reset = false) {
  if (loading.value) {
    return;
  }

  if (!reset && finished.value) {
    return;
  }

  if (reset) {
    projects.value = [];
    pageNo.value = 1;
    finished.value = false;
    errorText.value = '';
  }

  loading.value = true;
  const res = await axiosPostSilent('/wiki/wikiProject/list', {
    pageNo: pageNo.value,
    pageSize,
    orders: [{order: 'createDate', direction: 'desc'}]
  });
  loading.value = false;

  if (!res || res.code !== 2000) {
    errorText.value = res?.msg || '项目列表加载失败';
    finished.value = true;
    return;
  }

  const list = normalizeProjectList(res.data);
  projects.value = reset ? list : [...projects.value, ...list];
  pageNo.value += 1;

  if (list.length < pageSize) {
    finished.value = true;
  }
}

async function enterWiki(project = selectedProject.value) {
  if (!project || entering.value) {
    return;
  }

  entering.value = true;
  selectedProjectId.value = project.id;
  const res = await axiosPostSilent('/wiki/wikiProject/tables', {id: project.id});
  entering.value = false;

  if (!res || res.code !== 2000) {
    errorText.value = res?.msg || 'Wiki 项目结果加载失败';
    return;
  }

  sendPageData(ROUTER_NAMES.WIKI_VIEW, {
    wikiProject: project,
    wikiTables: res.data
  });
  goNextPage(ROUTER_NAMES.WIKI_VIEW);
}

onMounted(() => {
  loadProjects(true);
  initInfiniteObserver();
});

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect();
  }
});
</script>

<style scoped lang="scss">
.wiki-choose-page {
  min-height: 100%;
  padding: 36px 42px 56px;
  color: #2d2922;
}

.choose-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 26px;
  margin-bottom: 28px;
  padding: 28px 30px;
  border: 1px solid rgba(92, 72, 39, 0.18);
  border-radius: 8px;
  background: rgba(255, 250, 238, 0.5);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.36), 0 18px 42px rgba(78, 57, 30, 0.06);
  backdrop-filter: blur(5px);
}

.choose-hero__eyebrow {
  margin: 0 0 8px;
  font-family: Georgia, serif;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: rgba(78, 64, 43, 0.62);
}

.choose-hero h1 {
  margin: 0;
  font-size: 34px;
  line-height: 1.2;
}

.choose-hero p {
  max-width: 620px;
  margin: 12px 0 0;
  color: #766d5e;
  font-size: 16px;
  line-height: 1.8;
}

.paper-button,
.enter-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgba(110, 83, 42, 0.28);
  border-radius: 8px;
  font: inherit;
  cursor: pointer;
}

.paper-button {
  flex: 0 0 auto;
  min-width: 92px;
  height: 40px;
  padding: 0 16px;
  background: rgba(255, 250, 238, 0.68);
  color: #2d2922;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.42), 0 8px 18px rgba(91, 67, 34, 0.06);
}

.paper-button:disabled {
  cursor: default;
  opacity: 0.6;
}

.choose-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.choose-card {
  position: relative;
  min-height: 250px;
  padding: 24px;
  border: 1px solid rgba(92, 72, 39, 0.2);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.56);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.34), 0 18px 40px rgba(74, 55, 28, 0.07);
  backdrop-filter: blur(5px);
  cursor: pointer;
  overflow: hidden;
  transition: border-color 160ms ease, box-shadow 160ms ease, transform 160ms ease;
}

.choose-card:hover,
.choose-card--selected {
  border-color: rgba(167, 122, 45, 0.48);
  box-shadow: inset 0 0 0 1px rgba(255, 249, 232, 0.6), 0 20px 44px rgba(95, 69, 31, 0.1);
  transform: translateY(-1px);
}

.choose-card::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(circle at 22% 16%, rgba(167, 122, 45, 0.14), transparent 32%),
    linear-gradient(135deg, rgba(255, 255, 255, 0.26), transparent 46%);
}

.choose-card__header,
.choose-card h2,
.choose-card__desc,
.choose-card__footer {
  position: relative;
  z-index: 1;
}

.choose-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 20px;
}

.choose-card__seal {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  border: 1px solid rgba(73, 58, 35, 0.38);
  border-radius: 50%;
  color: #2d2922;
  font-size: 26px;
}

.choose-card__status {
  padding: 5px 10px;
  border: 1px solid rgba(119, 89, 41, 0.24);
  border-radius: 999px;
  background: rgba(246, 239, 226, 0.72);
  color: #766d5e;
  font-size: 13px;
}

.choose-card__status.is-success {
  color: #73521e;
  background: rgba(180, 137, 51, 0.18);
}

.choose-card__status.is-error {
  color: #7e3429;
  background: rgba(145, 69, 48, 0.12);
}

.choose-card h2 {
  min-height: 68px;
  margin: 0;
  font-size: 26px;
  line-height: 1.32;
}

.choose-card__desc {
  min-height: 58px;
  margin: 12px 0 18px;
  color: #766d5e;
  font-size: 15px;
  line-height: 1.7;
}

.choose-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding-top: 16px;
  border-top: 1px solid rgba(78, 63, 39, 0.1);
}

.choose-card__footer > span {
  color: #766d5e;
  font-size: 13px;
}

.enter-button {
  height: 36px;
  padding: 0 13px;
  background: rgba(167, 122, 45, 0.18);
  color: #2d2922;
  font-size: 14px;
  font-weight: 700;
}

.enter-button:disabled {
  cursor: default;
  opacity: 0.58;
}

.paper-alert,
.empty-state,
.load-sentinel {
  color: #766d5e;
}

.paper-alert {
  margin-bottom: 18px;
  padding: 14px 16px;
  border: 1px solid rgba(126, 52, 41, 0.22);
  border-radius: 8px;
  background: rgba(255, 246, 235, 0.7);
  color: #7e3429;
}

.empty-state {
  display: grid;
  place-items: center;
  min-height: 360px;
  border: 1px dashed rgba(91, 70, 40, 0.24);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.28);
}

.empty-state .mdi {
  font-size: 44px;
}

.empty-state p {
  margin: 10px 0 0;
}

.load-sentinel {
  min-height: 52px;
  padding-top: 22px;
  text-align: center;
  font-size: 14px;
}

@media (max-width: 720px) {
  .wiki-choose-page {
    padding: 24px 18px 40px;
  }

  .choose-hero {
    align-items: flex-start;
    flex-direction: column;
    padding: 22px;
  }
}
</style>
