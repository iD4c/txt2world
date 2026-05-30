<template>
  <section class="wiki-project-page">
    <div class="wiki-project-page__header">
      <div>
        <p class="wiki-project-page__eyebrow">WIKI PROJECTS</p>
        <h1>Wiki项目管理</h1>
      </div>
      <div class="wiki-project-page__actions">
        <button class="paper-button paper-button--primary" type="button" @click="handleCreateProject">
          <i class="mdi mdi-plus"></i>
          <span>新增</span>
        </button>
        <button class="paper-button" type="button" :disabled="loading" @click="loadProjects(true)">
          <i class="mdi mdi-refresh"></i>
          <span>刷新</span>
        </button>
      </div>
    </div>

    <div v-if="errorText" class="paper-alert">
      {{ errorText }}
    </div>

    <div v-if="hasProjects" class="project-grid">
      <article v-for="project in projects" :key="project.id || project.name" class="project-card">
        <div class="project-card__top">
          <div class="project-card__icon">
            <i class="mdi mdi-book-open-variant-outline"></i>
          </div>
          <span class="project-card__status" :class="statusClass(project.parseStatus)">
            {{ project.parseStatus || '未知状态' }}
          </span>
        </div>

        <h2>{{ project.name || '未命名项目' }}</h2>

        <dl class="project-card__meta">
          <div>
            <dt>章节数</dt>
            <dd>{{ project.chapterNum ?? '-' }}</dd>
          </div>
          <div>
            <dt>切片大小</dt>
            <dd>{{ project.chunkSize ?? '-' }}</dd>
          </div>
          <div>
            <dt>创建时间</dt>
            <dd>{{ formatDate(project.createDate) }}</dd>
          </div>
        </dl>

        <div v-if="project.parseStatus !== '解析中'" class="project-card__actions">
          <button
              v-if="project.parseStatus === '解析失败'"
              class="project-card__reparse"
              type="button"
              :disabled="reparsingProjectId === project.id"
              @click="handleReparseProject(project)"
          >
            <i class="mdi mdi-reload"></i>
            <span>{{ reparsingProjectId === project.id ? '解析中' : '再次解析' }}</span>
          </button>
          <button
              class="project-card__delete"
              type="button"
              :disabled="deletingProjectId === project.id"
              @click="handleDeleteProject(project)"
          >
            <i class="mdi mdi-trash-can-outline"></i>
            <span>{{ deletingProjectId === project.id ? '删除中' : '删除' }}</span>
          </button>
        </div>
      </article>
    </div>

    <div v-else-if="!loading" class="empty-state">
      <i class="mdi mdi-bookshelf"></i>
      <p>还没有可管理的 Wiki 项目</p>
    </div>

    <div ref="sentinelRef" class="load-sentinel">
      <span v-if="loading">正在载入项目...</span>
      <span v-else-if="finished && hasProjects">已加载全部项目</span>
    </div>
  </section>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue";
import {axiosPostLoading, axiosPostSilent} from "@/api/axios.js";
import {goNextPage} from "@/global/commonBizFun.js";
import {ROUTER_NAMES} from "@/router.js";

const projects = ref([]);
const loading = ref(false);
const deletingProjectId = ref('');
const reparsingProjectId = ref('');
const finished = ref(false);
const errorText = ref('');
const pageNo = ref(1);
const pageSize = 12;
const sentinelRef = ref(null);

let observer;

const hasProjects = computed(() => projects.value.length > 0);

function handleCreateProject() {
  goNextPage(ROUTER_NAMES.WIKI_PROJECT_UPLOAD);
}

async function handleDeleteProject(project) {
  if (!project?.id || deletingProjectId.value) {
    return;
  }
  window.global_showConfirm(`确认删除 Wiki 项目「${project.name || '未命名项目'}」吗？该操作会同时删除存盘文件。`, async () => {
    deletingProjectId.value = project.id;
    const res = await axiosPostLoading('/wiki/wikiProject/dels', [project.id]);
    deletingProjectId.value = '';

    if (!res || res.code !== 2000) {
      errorText.value = res?.msg || '项目删除失败';
      return;
    }

    projects.value = projects.value.filter(item => item.id !== project.id);
  });
}

async function handleReparseProject(project) {
  if (!project?.id || reparsingProjectId.value) {
    return;
  }

  reparsingProjectId.value = project.id;
  const res = await axiosPostLoading('/wiki/wikiProject/reparse', {
    id: project.id
  });
  reparsingProjectId.value = '';

  if (!res || res.code !== 2000) {
    errorText.value = res?.msg || '再次解析启动失败';
    return;
  }

  project.parseStatus = '解析中';
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
.wiki-project-page {
  min-height: 100%;
  padding: 36px 42px 56px;
  color: #2d2922;
}

.wiki-project-page__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 26px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(84, 66, 38, 0.16);
}

.wiki-project-page__actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.wiki-project-page__eyebrow {
  margin: 0 0 8px;
  font-family: Georgia, serif;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: rgba(78, 64, 43, 0.62);
}

.wiki-project-page h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.paper-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 92px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid rgba(110, 83, 42, 0.28);
  border-radius: 8px;
  background: rgba(255, 250, 238, 0.62);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.42), 0 8px 18px rgba(91, 67, 34, 0.06);
  color: #2d2922;
  font: inherit;
  cursor: pointer;
}

.paper-button:disabled {
  cursor: default;
  opacity: 0.6;
}

.paper-button--primary {
  border-color: rgba(137, 97, 33, 0.38);
  background: rgba(178, 133, 45, 0.18);
  color: #5f4217;
  font-weight: 700;
}

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 18px;
}

.project-card {
  position: relative;
  min-height: 226px;
  padding: 22px 22px 20px;
  border: 1px solid rgba(92, 72, 39, 0.22);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.62);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.34), 0 18px 38px rgba(74, 55, 28, 0.08);
  backdrop-filter: blur(5px);
  overflow: hidden;
}

.project-card::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: radial-gradient(circle at 18% 10%, rgba(174, 130, 47, 0.12), transparent 34%);
}

.project-card__top,
.project-card h2,
.project-card__meta,
.project-card__actions {
  position: relative;
  z-index: 1;
}

.project-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 18px;
}

.project-card__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(73, 58, 35, 0.38);
  border-radius: 50%;
  color: #2d2922;
  font-size: 23px;
}

.project-card__status {
  padding: 5px 10px;
  border: 1px solid rgba(119, 89, 41, 0.24);
  border-radius: 999px;
  background: rgba(246, 239, 226, 0.72);
  color: #766d5e;
  font-size: 13px;
}

.project-card__status.is-success {
  color: #73521e;
  background: rgba(180, 137, 51, 0.18);
}

.project-card__status.is-error {
  color: #7e3429;
  background: rgba(145, 69, 48, 0.12);
}

.project-card h2 {
  min-height: 62px;
  margin: 0 0 18px;
  font-size: 24px;
  line-height: 1.35;
}

.project-card__meta {
  display: grid;
  gap: 12px;
  margin: 0;
}

.project-card__meta div {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 16px;
  border-top: 1px solid rgba(78, 63, 39, 0.1);
  padding-top: 10px;
}

.project-card__meta dt {
  color: #766d5e;
  font-size: 14px;
}

.project-card__meta dd {
  margin: 0;
  text-align: right;
  font-size: 15px;
  font-weight: 700;
}

.project-card__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

.project-card__delete,
.project-card__reparse {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(125, 54, 42, 0.26);
  border-radius: 7px;
  background: rgba(146, 64, 48, 0.08);
  color: #7e3429;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}

.project-card__reparse {
  border-color: rgba(137, 97, 33, 0.28);
  background: rgba(178, 133, 45, 0.12);
  color: #684916;
}

.project-card__delete:disabled,
.project-card__reparse:disabled {
  cursor: default;
  opacity: 0.6;
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
  .wiki-project-page {
    padding: 24px 18px 40px;
  }

  .wiki-project-page__header {
    align-items: flex-start;
    flex-direction: column;
  }

  .wiki-project-page__actions {
    width: 100%;
  }

  .paper-button {
    flex: 1;
  }
}
</style>
