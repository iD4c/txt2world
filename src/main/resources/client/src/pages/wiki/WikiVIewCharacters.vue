<template>
  <section class="wiki-entity-list">
    <div v-if="visibleCharacters.length" class="character-grid">
      <article v-for="character in visibleCharacters" :key="character.name" class="character-card">
        <div class="character-card__portrait">
          <i class="mdi mdi-account-outline"></i>
        </div>
        <div class="character-card__body">
          <h2>{{ character.name || '未命名人物' }}</h2>
          <p>{{ formatAliases(character.aliases) }}</p>
          <v-btn class="story-button" variant="flat" size="small" @click="openStory(character)">
            <v-icon start icon="mdi-book-open-page-variant-outline"/>
            剧情
          </v-btn>
        </div>
      </article>
    </div>

    <div v-else class="empty-state">暂无人物数据</div>

    <div ref="sentinelRef" class="load-sentinel">
      <span v-if="visibleCharacters.length < normalizedCharacters.length">继续下滑加载更多人物</span>
      <span v-else-if="visibleCharacters.length">已加载全部人物</span>
    </div>

    <v-dialog v-model="storyDialogVisible" max-width="720">
      <div class="story-dialog">
        <div class="story-dialog__header">
          <h2>{{ selectedEntity?.name || '人物剧情' }}</h2>
          <button type="button" @click="storyDialogVisible = false">
            <i class="mdi mdi-close"></i>
          </button>
        </div>
        <div class="story-dialog__body">
          <div v-for="summary in selectedStory" :key="summary.chunkIndex" class="story-entry">
            <div class="story-entry__chunk">{{ formatChunkRange(summary.chunkIndex) }}</div>
            <p>{{ summary.summaryText || '暂无剧情' }}</p>
          </div>
          <div v-if="!selectedStory.length" class="empty-story">暂无剧情</div>
        </div>
      </div>
    </v-dialog>
  </section>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue";

const props = defineProps({
  characters: {
    type: Array,
    default: () => []
  }
});

const visibleCount = ref(16);
const storyDialogVisible = ref(false);
const selectedEntity = ref(null);
const sentinelRef = ref(null);

let observer;

const normalizedCharacters = computed(() => normalizeList(props.characters));
const visibleCharacters = computed(() => normalizedCharacters.value.slice(0, visibleCount.value));
const selectedStory = computed(() => normalizeList(selectedEntity.value?.chunkSummaryList)
  .slice()
  .sort((left, right) => (left.chunkIndex || 0) - (right.chunkIndex || 0)));

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}

function formatAliases(aliases) {
  const text = normalizeList(aliases)
    .map(alias => alias.alias || alias)
    .filter(Boolean)
    .join('、');
  return text || '暂无别名';
}

function formatChunkRange(chunkIndex) {
  const index = Number.isInteger(chunkIndex) ? chunkIndex : 0;
  return `${index * 20 + 1}-${(index + 1) * 20}章`;
}

function loadMore() {
  if (visibleCount.value >= normalizedCharacters.value.length) {
    return;
  }
  visibleCount.value += 12;
}

function openStory(entity) {
  selectedEntity.value = entity;
  storyDialogVisible.value = true;
}

function initObserver() {
  if (!sentinelRef.value) {
    return;
  }
  observer = new IntersectionObserver((entries) => {
    if (entries[0]?.isIntersecting) {
      loadMore();
    }
  }, {
    root: sentinelRef.value.closest('.wiki-entity-list'),
    rootMargin: '260px 0px'
  });
  observer.observe(sentinelRef.value);
}

watch(
  () => props.characters,
  () => {
    visibleCount.value = 16;
  }
);

onMounted(() => {
  initObserver();
});

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect();
  }
});
</script>

<style scoped lang="scss">
.wiki-entity-list {
  height: 100%;
  overflow: hidden auto;
  padding: 26px 30px 46px;
}

.character-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 18px;
}

.character-card {
  overflow: hidden;
  border: 1px solid rgba(92, 72, 39, 0.22);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.62);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.34), 0 18px 38px rgba(74, 55, 28, 0.08);
  backdrop-filter: blur(5px);
}

.character-card__portrait {
  display: grid;
  place-items: center;
  aspect-ratio: 4 / 3;
  background:
    radial-gradient(circle at 50% 38%, rgba(167, 122, 45, 0.16), transparent 34%),
    linear-gradient(180deg, rgba(246, 239, 226, 0.62), rgba(225, 207, 171, 0.34));
  color: rgba(45, 41, 34, 0.42);
  font-size: 54px;
}

.character-card__body {
  padding: 16px;
}

.character-card h2 {
  margin: 0 0 10px;
  font-size: 20px;
}

.character-card p {
  display: -webkit-box;
  min-height: 44px;
  margin: 0 0 14px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  color: rgba(45, 41, 34, 0.66);
  line-height: 1.6;
}

.story-button {
  color: #5f4217;
  background: rgba(178, 133, 45, 0.18);
  letter-spacing: 0;
  text-transform: none;
}

.load-sentinel,
.empty-state,
.empty-story {
  padding: 28px;
  text-align: center;
  color: rgba(45, 41, 34, 0.58);
}

.story-dialog {
  border: 1px solid rgba(92, 72, 39, 0.24);
  border-radius: 8px;
  background: #fbf3e3;
  color: #2d2922;
  box-shadow: 0 24px 60px rgba(47, 35, 18, 0.22);
}

.story-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 22px;
  border-bottom: 1px solid rgba(84, 66, 38, 0.16);
}

.story-dialog__header h2 {
  margin: 0;
  font-size: 22px;
}

.story-dialog__header button {
  border: 0;
  background: transparent;
  color: #2d2922;
  cursor: pointer;
  font-size: 22px;
}

.story-dialog__body {
  max-height: 62vh;
  overflow: hidden auto;
  padding: 18px 22px 22px;
}

.story-entry {
  padding: 14px 0;
  border-bottom: 1px solid rgba(84, 66, 38, 0.12);
}

.story-entry__chunk {
  margin-bottom: 8px;
  color: #8a6425;
  font-weight: 700;
}

.story-entry p {
  margin: 0;
  color: rgba(45, 41, 34, 0.74);
  line-height: 1.75;
}
</style>
