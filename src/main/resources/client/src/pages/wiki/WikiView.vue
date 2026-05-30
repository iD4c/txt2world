<template>
  <div class="home-page">
    <div class="tabs">
      <button type="button" :class="{'active': activeTab === 'relation'}" @click="activeTab = 'relation'">✥ 关系图</button>
      <button type="button" :class="{'active': activeTab === 'characters'}" @click="activeTab = 'characters'">✦ 人物</button>
      <button type="button" :class="{'active': activeTab === 'items'}" @click="activeTab = 'items'">◇ 物品</button>
      <button type="button" :class="{'active': activeTab === 'factions'}" @click="activeTab = 'factions'">⚑ 势力</button>
    </div>

    <div class="home-body">
      <template v-if="activeTab === 'relation'">
        <section class="star-map-panel">
          <div class="map-stage">
            <CharacterStarGraph
                :graph-data="graphData"
                :selected-node-id="selectedNodeId"
                :selected-link-id="selectedLinkId"
                @select-node="selectNode"
                @select-link="selectLink"
                @clear-selection="clearSelection"
            />
          </div>
          <div class="map-hint">人物关系连线仅显示主要关系</div>
        </section>

        <section class="chapter-panel floating-panel" :class="{'floating-panel--collapsed': leftPanelCollapsed}">
          <button class="panel-toggle panel-toggle--left" type="button" @click="leftPanelCollapsed = !leftPanelCollapsed">
            <i class="mdi" :class="leftPanelCollapsed ? 'mdi-chevron-right' : 'mdi-chevron-left'"></i>
          </button>

          <div v-if="leftPanelCollapsed" class="collapsed-label">章节切片</div>
          <template v-else>
            <div class="panel-title">✦ 当前章节切片</div>
            <div class="chapter-actions">
              <v-btn
                  class="chunk-nav-button"
                  type="button"
                  variant="flat"
                  elevation="0"
                  :disabled="!hasPrevChunk"
                  @click="changeChunk(-1)"
              >
                <v-icon icon="mdi-arrow-left"></v-icon>
                <span>上一切片</span>
              </v-btn>
              <v-btn
                  class="chunk-nav-button"
                  type="button"
                  variant="flat"
                  elevation="0"
                  :disabled="!hasNextChunk"
                  @click="changeChunk(1)"
              >
                <span>下一切片</span>
                <v-icon icon="mdi-arrow-right"></v-icon>
              </v-btn>
            </div>

            <div class="summary-switch">
              <button
                type="button"
                :class="{'active': summaryMode === 'chunk'}"
                @click="summaryMode = 'chunk'"
              >
                切片摘要
              </button>
              <button
                type="button"
                :class="{'active': summaryMode === 'chapter'}"
                @click="summaryMode = 'chapter'"
              >
                章节摘要
              </button>
            </div>
            <v-tooltip :text="currentSummaryText" location="top" max-width="500">
              <template #activator="{ props: tooltipProps }">
                <p v-bind="tooltipProps" class="summary summary--clamped">
                  {{ currentSummaryText }}
                </p>
              </template>
            </v-tooltip>

            <div class="section-title">包含章节</div>
            <div class="chapter-grid">
              <button
                  v-for="chapter in currentChapters"
                  :key="chapter.chapterSeq"
                  type="button"
                  :class="{'active': chapter.chapterSeq === activeChapterSeq}"
                  @click="selectChapter(chapter.chapterSeq)"
              >
                {{ chapter.chapterSeq }}
              </button>
            </div>
          </template>
        </section>

        <aside class="detail-panel floating-panel" :class="{'floating-panel--collapsed': rightPanelCollapsed}">
          <button class="panel-toggle panel-toggle--right" type="button" @click="rightPanelCollapsed = !rightPanelCollapsed">
            <i class="mdi" :class="rightPanelCollapsed ? 'mdi-chevron-left' : 'mdi-chevron-right'"></i>
          </button>

          <div v-if="rightPanelCollapsed" class="collapsed-label">关系详情</div>
          <template v-else>
            <div class="panel-title detail-panel__title">✦ {{ detailTitle }}</div>
            <div class="tag">{{ detailTag }}</div>
            <div class="detail-names">{{ detailNames }}</div>

            <div v-if="selectedNode" class="character-detail-list">
              <div v-for="item in characterDetailRows" :key="item.label" class="character-detail-row">
                <div class="character-detail-row__label">{{ item.label }}</div>
                <v-tooltip :text="item.value" location="top" max-width="500">
                  <template #activator="{ props: tooltipProps }">
                    <div v-bind="tooltipProps" class="character-detail-row__value">{{ item.value }}</div>
                  </template>
                </v-tooltip>
              </div>
            </div>

            <template v-else>
            <div class="summary-block">
              <div class="section-title">关系摘要</div>
              <v-tooltip :text="detailSummary" location="top" max-width="500">
                <template #activator="{ props: tooltipProps }">
                  <p v-bind="tooltipProps" class="summary summary--clamped">
                    {{ detailSummary }}
                  </p>
                </template>
              </v-tooltip>
            </div>

            <div class="divider"></div>

            <div class="relation-dimension">
              <div class="relation-dimension__title">态度</div>
              <div class="relation-dimension__body relation-dimension__body--energy">
                <div v-for="direction in relationDirections" :key="`attitude-${direction.key}`" class="energy-side">
                  <div class="energy-label" :class="{'energy-label--empty': direction.attitudeLabel === '无'}">
                    {{ direction.attitudeLabel }}
                  </div>
                  <div class="energy-cells energy-cells--attitude">
                    <v-tooltip
                        v-for="cell in direction.attitudeCells"
                        :key="cell.index"
                        :text="cell.label"
                        location="top"
                        max-width="500"
                    >
                      <template #activator="{ props: tooltipProps }">
                        <i v-bind="tooltipProps" :class="{'is-active': cell.active}"></i>
                      </template>
                    </v-tooltip>
                  </div>
                </div>
              </div>
            </div>

            <div class="relation-dimension">
              <div class="relation-dimension__title">层级</div>
              <div class="relation-dimension__body">
                <div class="relation-text-pair">
                  <span>{{ detailProfile.sourceLevel || '无' }}</span>
                  <i></i>
                  <span>{{ detailProfile.targetLevel || '无' }}</span>
                </div>
              </div>
            </div>

            <div class="relation-dimension">
              <div class="relation-dimension__title">纽带</div>
              <div class="relation-dimension__body">
                <div class="relation-bond">{{ detailProfile.bond || '无' }}</div>
              </div>
            </div>
            </template>
          </template>
        </aside>
      </template>

      <WikiVIewCharacters
          v-else-if="activeTab === 'characters'"
          :characters="mergedCharacters"
      />
      <WikiVIewItems
          v-else-if="activeTab === 'items'"
          :items="mergedItems"
      />
      <WikiVIewFactions
          v-else
          :factions="mergedFactions"
      />
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from "vue";
import {getPageData} from "@/global/commonBizFun.js";
import CharacterStarGraph from "@/pages/components/CharacterStarGraph.vue";
import WikiVIewCharacters from "@/pages/wiki/WikiVIewCharacters.vue";
import WikiVIewFactions from "@/pages/wiki/WikiVIewFactions.vue";
import WikiVIewItems from "@/pages/wiki/WikiVIewItems.vue";

const leftPanelCollapsed = ref(false);
const rightPanelCollapsed = ref(false);
const currentChunkIndex = ref(0);
const selectedChapterSeq = ref(null);
const summaryMode = ref('chunk');
const selectedNodeId = ref('');
const selectedLinkId = ref('');
const activeTab = ref('relation');
const attitudeScale = ['敌对', '欺骗', '利用', '对峙', '怀疑', '中立', '合作', '友好'];
const pageData = getPageData();
const emptyRelationProfile = {
  attitude: {},
  affection: {},
  sourceLevel: '无',
  targetLevel: '无',
  bond: '无',
  narrativeRole: {}
};
const wikiTables = pageData.wikiTables || {};

const chunkSummaryList = computed(() => normalizeList(wikiTables.chunkSummaryList));
const draftAlignedList = computed(() => normalizeList(wikiTables.aiOutInfoDraftAlignedList));
const relationExtractList = computed(() => normalizeList(wikiTables.aiOutInfoRelationExtractList));
const mergedCharacters = computed(() => normalizeList(wikiTables.mergedWikiDataCharacters));
const mergedItems = computed(() => normalizeList(wikiTables.mergedWikiDataItems));
const mergedFactions = computed(() => normalizeList(wikiTables.mergedWikiDataFactions));
const currentChunkSummary = computed(() => {
  return chunkSummaryList.value.find(item => item.chunkIndex === currentChunkIndex.value)
      || chunkSummaryList.value[0]
      || {};
});
const currentChapters = computed(() => {
  const chapters = normalizeList(currentChunkSummary.value.chapters);
  const chapterMap = new Map(chapters.map(chapter => [chapter.chapterSeq, chapter]));
  const start = currentChunkSummary.value.startChapterSeq || chapters[0]?.chapterSeq;
  const end = currentChunkSummary.value.endChapterSeq || chapters[chapters.length - 1]?.chapterSeq;

  if (!start || !end) {
    return chapters;
  }

  return Array.from({length: end - start + 1}, (_, index) => {
    const chapterSeq = start + index;
    return chapterMap.get(chapterSeq) || {
      chapterSeq,
      chapterSummary: '暂无章节摘要'
    };
  });
});
const activeChapter = computed(() => {
  return currentChapters.value.find(chapter => chapter.chapterSeq === selectedChapterSeq.value)
      || currentChapters.value[0]
      || {};
});
const activeChapterSeq = computed(() => activeChapter.value.chapterSeq);
const currentSummaryText = computed(() => {
  if (summaryMode.value === 'chapter') {
    return activeChapter.value.chapterSummary || '暂无章节摘要';
  }
  return currentChunkSummary.value.chunkSummary || '暂无切片摘要';
});
const hasPrevChunk = computed(() => currentChunkIndex.value > 0);
const hasNextChunk = computed(() => currentChunkIndex.value < chunkSummaryList.value.length - 1);
const characterByName = computed(() => {
  return new Map(graphCharacters.value.map(character => [character.name, character]));
});
const graphCharacters = computed(() => {
  const nodes = buildGraphCharactersForChunk(currentChunkIndex.value);
  const previousNodeIds = new Set(buildGraphCharactersForChunk(currentChunkIndex.value - 1).map(node => node.id));
  const nodeNames = new Set(nodes.map(character => character.name));
  const relations = buildGraphRelationsForChunk(currentChunkIndex.value, nodeNames);
  const centerCharacterId = findMostConnectedCharacterId(nodes, relations);

  return nodes.map(node => ({
    ...node,
    isNewInCurrentChunk: !previousNodeIds.has(node.id),
    isGraphCenter: node.id === centerCharacterId
  }));
});
const graphRelations = computed(() => {
  const nodeNames = new Set(graphCharacters.value.map(character => character.name));
  const previousNodeNames = new Set(buildGraphCharactersForChunk(currentChunkIndex.value - 1).map(character => character.name));
  const previousRelationIds = new Set(buildGraphRelationsForChunk(currentChunkIndex.value - 1, previousNodeNames).map(relation => relation.id));

  return buildGraphRelationsForChunk(currentChunkIndex.value, nodeNames).map(relation => ({
    ...relation,
    isNewInCurrentChunk: !previousRelationIds.has(relation.id)
  }));
});
const graphData = computed(() => {
  return {
    nodes: graphCharacters.value,
    links: graphRelations.value
  };
});
const selectedNode = computed(() => {
  if (!selectedNodeId.value) {
    return null;
  }
  return characterByName.value.get(selectedNodeId.value) || null;
});
const selectedLink = computed(() => {
  return graphRelations.value.find(relation => relation.id === selectedLinkId.value)
      || graphRelations.value[0]
      || null;
});
const detailTitle = computed(() => selectedNode.value ? '人物详情' : '关系详情');
const detailTag = computed(() => selectedNode.value ? '人物节点' : '关系边');
const detailSourceName = computed(() => {
  if (!selectedLink.value) {
    return '左侧人物';
  }
  return selectedLink.value.source || '左侧人物';
});
const detailTargetName = computed(() => {
  if (!selectedLink.value) {
    return '右侧人物';
  }
  return selectedLink.value.target || '右侧人物';
});
const detailNames = computed(() => {
  if (selectedNode.value) {
    return selectedNode.value.name;
  }

  if (!selectedLink.value) {
    return '克莱恩 ✧ 邓恩';
  }

  return `${detailSourceName.value} ✧ ${detailTargetName.value}`;
});
const characterDetailRows = computed(() => {
  if (!selectedNode.value) {
    return [];
  }
  const raw = selectedNode.value.raw || {};
  return [
    {label: '名称', value: raw.name || selectedNode.value.name || '无'},
    {label: '重要级别', value: raw.importanceLevel || '无'},
    {label: '状态', value: raw.lifeStatus || '活着'},
    {label: '别名', value: formatAliases(raw.aliases)},
    {label: '特征', value: normalizeList(raw.identInfos).join('、') || '无'},
    {label: '摘要', value: selectedNode.value.summary || '无'}
  ];
});
const detailSummary = computed(() => {
  if (selectedNode.value) {
    return selectedNode.value.summary;
  }
  return selectedLink.value?.summary || '点击人物节点或关系边，可查看对应人物或关系在当前切片中的摘要。';
});
const detailProfile = computed(() => {
  if (!selectedLink.value) {
    return emptyRelationProfile;
  }
  return selectedLink.value.relationProfile || emptyRelationProfile;
});
const relationDirections = computed(() => {
  const sourceToTarget = detailProfile.value.attitude?.sourceToTarget;
  const targetToSource = detailProfile.value.attitude?.targetToSource;

  return [
    {
      key: 'source-to-target',
      attitudeLabel: scaleLabel(attitudeScale, sourceToTarget),
      attitudeCells: buildEnergyCells(attitudeScale, sourceToTarget)
    },
    {
      key: 'target-to-source',
      attitudeLabel: scaleLabel(attitudeScale, targetToSource),
      attitudeCells: buildEnergyCells(attitudeScale, targetToSource, true)
    }
  ];
});

function scaleLabel(scale, value) {
  return scale.includes(value) ? value : '无';
}

function normalizeList(value) {
  return Array.isArray(value) ? value : [];
}

function uniqueList(list) {
  return [...new Set(list.filter(Boolean))];
}

function relationKey(nameA, nameB) {
  return [nameA, nameB].sort((left, right) => left.localeCompare(right, 'zh-Hans-CN')).join('__');
}

function getDraftByChunkIndex(chunkIndex) {
  if (chunkIndex < 0) {
    return {};
  }
  return draftAlignedList.value.find(item => item.chunkIndex === chunkIndex)
      || draftAlignedList.value[chunkIndex]
      || {};
}

function shouldRetainAbsentCharacter(character, chunkIndex) {
  if (['CORE', 'HIGH'].includes(character.importanceLevel)) {
    return true;
  }
  if (character.importanceLevel !== 'MEDIUM') {
    return false;
  }

  const lastAppearanceChunkIndex = findLastAppearanceChunkIndex(character.name, chunkIndex);
  return lastAppearanceChunkIndex >= 0 && chunkIndex - lastAppearanceChunkIndex < 3;
}

function findLastAppearanceChunkIndex(characterName, chunkIndex) {
  for (let index = chunkIndex; index >= 0; index -= 1) {
    const hasAppeared = normalizeList(getDraftByChunkIndex(index).characters)
      .filter(isCharacterWorldObj)
      .some(character => character?.name === characterName);
    if (hasAppeared) {
      return index;
    }
  }
  return -1;
}

function buildGraphCharactersForChunk(chunkIndex) {
  if (chunkIndex < 0) {
    return [];
  }

  const characterMap = new Map();
  const currentDraftForGraph = getDraftByChunkIndex(chunkIndex);
  const currentNames = new Set(normalizeList(currentDraftForGraph.characters)
    .filter(isCharacterWorldObj)
    .map(character => character.name)
    .filter(Boolean));

  for (let index = 0; index <= chunkIndex; index += 1) {
    normalizeList(getDraftByChunkIndex(index).characters).filter(isCharacterWorldObj).forEach(character => {
      if (character?.name) {
        characterMap.set(character.name, character);
      }
    });
  }

  const characters = [...characterMap.values()].filter(character => {
    return currentNames.has(character.name) || shouldRetainAbsentCharacter(character, chunkIndex);
  });

  return characters.map((character, index, list) => {
    const chunkSummary = normalizeList(character.chunkSummaryList)
      .filter(item => item.chunkIndex <= chunkIndex);
    const latestChunkSummary = chunkSummary[chunkSummary.length - 1];
    const positioned = calcNodePosition(index, list.length, character.importanceLevel);

    return {
      ...positioned,
      id: character.name,
      name: character.name,
      type: 'character',
      importanceLevel: character.importanceLevel,
      lifeStatus: character.lifeStatus,
      importance: character.importanceLevel === 'CORE' ? 'core' : 'normal',
      summary: latestChunkSummary?.summaryText || character.summaryText || '',
      raw: character
    };
  });
}

function buildGraphRelationsForChunk(chunkIndex, availableNames) {
  if (chunkIndex < 0) {
    return [];
  }

  const grouped = new Map();
  relationExtractList.value.forEach(relationExtract => {
    normalizeList(relationExtract.characterRelations).forEach(relation => {
      const relationChunkIndex = relation.chunkIndex ?? relationExtract.chunkIndex ?? 0;
      if (relationChunkIndex > chunkIndex) {
        return;
      }

      const nameA = relation.characterA?.name;
      const nameB = relation.characterB?.name;
      if (!nameA || !nameB || !availableNames.has(nameA) || !availableNames.has(nameB)) {
        return;
      }

      const key = relationKey(nameA, nameB);
      if (!grouped.has(key)) {
        grouped.set(key, {
          id: key,
          source: nameA,
          target: nameB,
          summaries: [],
          relations: [],
          latest: relation
        });
      }

      const group = grouped.get(key);
      const relationWithChunkIndex = {
        ...relation,
        chunkIndex: relationChunkIndex
      };
      group.relations.push(relationWithChunkIndex);
      if (relation.relationSummary) {
        group.summaries.push(relation.relationSummary);
      }
      if (relationChunkIndex > (group.latest.chunkIndex || -1)
          || (relationChunkIndex === group.latest.chunkIndex && (relation.toChapterSeq || 0) > (group.latest.toChapterSeq || 0))) {
        group.latest = relationWithChunkIndex;
        group.source = nameA;
        group.target = nameB;
      }
    });
  });

  return [...grouped.values()].map(group => {
    const profile = buildRelationProfile(group.source, group.target, group.latest);
    return {
      id: group.id,
      source: group.source,
      target: group.target,
      label: buildRelationEdgeLabel(profile),
      summary: uniqueList(group.summaries).join('\n'),
      relationProfile: profile,
      isNewInCurrentChunk: false
    };
  });
}

function isCharacterWorldObj(worldObj) {
  return !worldObj?.wikiSectionType || worldObj.wikiSectionType === '人物';
}

function buildRelationProfile(sourceName, targetName, relation) {
  const characterA = relation.characterA || {};
  const characterB = relation.characterB || {};
  const sourceDimensions = characterA.name === sourceName
    ? characterA.relationDimensionsToOther
    : characterB.relationDimensionsToOther;
  const targetDimensions = characterA.name === targetName
    ? characterA.relationDimensionsToOther
    : characterB.relationDimensionsToOther;

  return {
    attitude: {
      sourceToTarget: sourceDimensions?.attitude || '无',
      targetToSource: targetDimensions?.attitude || '无'
    },
    affection: {
      sourceToTarget: sourceDimensions?.favorability || '无',
      targetToSource: targetDimensions?.favorability || '无'
    },
    sourceLevel: sourceDimensions?.hierarchy || '无',
    targetLevel: targetDimensions?.hierarchy || '无',
    bond: mergeBond(sourceDimensions?.bond, targetDimensions?.bond),
    narrativeRole: {
      sourceToTarget: sourceDimensions?.narrativeRole || '无',
      targetToSource: targetDimensions?.narrativeRole || '无'
    }
  };
}

function mergeBond(sourceBond, targetBond) {
  const sourceValue = normalizeRelationValue(sourceBond);
  const targetValue = normalizeRelationValue(targetBond);
  if (sourceValue === '无' && targetValue === '无') {
    return '无';
  }
  return `${sourceValue} / ${targetValue}`;
}

function buildRelationEdgeLabel(profile) {
  return profile.bond && profile.bond !== '无' ? profile.bond : '';
}

function normalizeRelationValue(value) {
  return value && value !== '无' ? value : '无';
}

function formatAliases(aliases) {
  const text = normalizeList(aliases)
    .map(alias => alias.alias || alias)
    .filter(Boolean)
    .join('、');
  return text || '无';
}

function calcNodePosition(index, count, importanceLevel) {
  if (importanceLevel === 'CORE' || index === 0) {
    return {x: 430, y: 315};
  }
  const angle = -Math.PI / 2 + (Math.PI * 2 * (index - 1)) / Math.max(count - 1, 1);
  return {
    x: 430 + Math.cos(angle) * 285,
    y: 315 + Math.sin(angle) * 210
  };
}

function buildEnergyCells(scale, value, reverse = false) {
  const level = scale.includes(value) ? scale.indexOf(value) + 1 : 0;
  return scale.map((label, index) => ({
    index,
    label: reverse ? scale[scale.length - 1 - index] : label,
    active: reverse ? index >= scale.length - level : index < level
  }));
}

function changeChunk(offset) {
  const nextIndex = currentChunkIndex.value + offset;
  if (nextIndex < 0 || nextIndex >= chunkSummaryList.value.length) {
    return;
  }
  currentChunkIndex.value = nextIndex;
  selectedChapterSeq.value = null;
  summaryMode.value = 'chunk';
  selectMostConnectedCharacter();
}

function selectChapter(chapterSeq) {
  selectedChapterSeq.value = chapterSeq;
  summaryMode.value = 'chapter';
}

function selectNode(node) {
  selectedNodeId.value = node.id;
  selectedLinkId.value = '';
}

function selectLink(link) {
  selectedLinkId.value = link.id;
  selectedNodeId.value = '';
}

function clearSelection() {
  selectMostConnectedCharacter();
}

function selectMostConnectedCharacter() {
  selectedNodeId.value = findMostConnectedCharacterId(graphCharacters.value, graphRelations.value);
  selectedLinkId.value = '';
}

function findMostConnectedCharacterId(characters, relations) {
  const relationCountByName = new Map(characters.map(character => [character.name, 0]));
  relations.forEach(relation => {
    relationCountByName.set(relation.source, (relationCountByName.get(relation.source) || 0) + 1);
    relationCountByName.set(relation.target, (relationCountByName.get(relation.target) || 0) + 1);
  });

  return characters.reduce((bestCharacter, character) => {
    if (!bestCharacter) {
      return character;
    }

    const currentCount = relationCountByName.get(character.name) || 0;
    const bestCount = relationCountByName.get(bestCharacter.name) || 0;
    if (currentCount !== bestCount) {
      return currentCount > bestCount ? character : bestCharacter;
    }
    return character.importanceLevel === 'CORE' && bestCharacter.importanceLevel !== 'CORE' ? character : bestCharacter;
  }, null)?.id || '';
}

onMounted(() => {
  selectMostConnectedCharacter();
});
</script>

<style scoped lang="scss">
.home-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  padding: 18px 18px 0;
}

.home-body {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.floating-panel {
  position: absolute;
  top: 22px;
  z-index: 3;
  max-height: calc(100% - 44px);
  overflow: hidden auto;
  padding: 24px;
  border: 1px solid rgba(77, 58, 34, 0.34);
  border-radius: 8px;
  background:
    linear-gradient(180deg, rgba(255, 252, 243, 0.72), rgba(246, 235, 214, 0.58)),
    rgba(255, 250, 239, 0.58);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.52),
    inset 0 0 34px rgba(120, 87, 38, 0.06),
    0 18px 38px rgba(71, 52, 27, 0.13);
  backdrop-filter: blur(5px) saturate(0.86);
  scrollbar-color: rgba(129, 95, 43, 0.42) rgba(255, 249, 236, 0.24);
  scrollbar-width: thin;
}

.floating-panel::before,
.floating-panel::after {
  content: "";
  position: absolute;
  pointer-events: none;
}

.floating-panel::before {
  inset: 8px;
  border: 1px solid rgba(100, 76, 43, 0.13);
  border-radius: 5px;
}

.floating-panel::after {
  inset: 0;
  background:
    radial-gradient(circle at 10% 18%, rgba(129, 95, 43, 0.06), transparent 18%),
    radial-gradient(circle at 82% 78%, rgba(129, 95, 43, 0.05), transparent 20%);
  mix-blend-mode: multiply;
}

.floating-panel > * {
  position: relative;
  z-index: 1;
}

.floating-panel > .panel-toggle {
  z-index: 2;
}

.chapter-panel {
  left: 22px;
  width: 330px;
}

.detail-panel {
  right: 22px;
  width: 470px;
  padding-bottom: 40px;
}

.floating-panel--collapsed {
  width: 48px;
  min-height: 220px;
  padding: 52px 0 18px;
  overflow: hidden;
}

.panel-toggle {
  position: absolute;
  top: 14px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 1px solid rgba(91, 69, 40, 0.28);
  border-radius: 7px;
  background: rgba(255, 250, 239, 0.72);
  color: #2b251c;
  cursor: pointer;
}

.panel-toggle--left {
  right: 14px;
}

.panel-toggle--right {
  left: 14px;
}

.collapsed-label {
  position: relative;
  z-index: 1;
  margin: 0 auto;
  color: rgba(43, 37, 28, 0.78);
  font-weight: 700;
  letter-spacing: 3px;
  writing-mode: vertical-rl;
}

.panel-title,
.section-title {
  font-weight: 700;
  color: #2d261b;
}

.panel-title {
  margin-bottom: 22px;
  font-size: 19px;
}

.detail-panel__title {
  padding-left: 38px;
  text-align: right;
}

.chapter-actions {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.chapter-grid button,
.tabs button {
  border: 1px solid rgba(91, 69, 40, 0.28);
  border-radius: 7px;
  background: rgba(255, 250, 239, 0.58);
  color: #2b251c;
  cursor: pointer;
}

.chunk-nav-button {
  height: 34px;
  min-width: 0;
  padding: 0 10px;
  border: 1px solid rgba(91, 69, 40, 0.28);
  border-radius: 7px;
  background: rgba(255, 250, 239, 0.58);
  color: #2b251c;
  font-size: 14px;
  letter-spacing: 0;
  text-transform: none;
}

.section-title {
  margin: 22px 0 10px;
  font-size: 16px;
}

.summary-switch {
  display: flex;
  gap: 8px;
  margin: 22px 0 10px;
}

.summary-switch button {
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(91, 69, 40, 0.24);
  border-radius: 7px;
  background: rgba(255, 250, 239, 0.52);
  color: #2b251c;
  cursor: pointer;
  font-weight: 700;
}

.summary-switch button.active {
  background: rgba(165, 125, 44, 0.22);
  color: #6c4f1b;
}

.summary {
  margin: 0;
  color: rgba(43, 37, 28, 0.72);
  font-size: 15px;
  line-height: 1.75;
}

.summary--clamped {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  cursor: help;
}

.chapter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
}

.chapter-grid button {
  height: 38px;
  font-size: 17px;
}

.chapter-grid button.active {
  background: #a57d2c;
  color: #fff8e8;
}

.star-map-panel {
  position: absolute;
  inset: 0;
  z-index: 1;
  min-width: 0;
}

.tabs {
  display: flex;
  align-items: center;
  gap: 58px;
  height: 44px;
  padding-left: 28px;
  border-bottom: 1px solid rgba(73, 57, 35, 0.14);
}

.tabs button {
  position: relative;
  height: 44px;
  padding: 0 8px;
  border: 0;
  background: transparent;
  font-size: 18px;
}

.tabs button.active {
  font-weight: 700;
}

.tabs button.active::after {
  content: "";
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 3px;
  background: #9f7829;
}

.map-stage {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.map-hint {
  position: absolute;
  left: 50%;
  bottom: 18px;
  z-index: 2;
  transform: translateX(-50%);
  padding: 6px 14px;
  border: 1px solid rgba(97, 75, 45, 0.16);
  border-radius: 999px;
  background: rgba(255, 250, 239, 0.48);
  color: rgba(45, 41, 34, 0.56);
  font-size: 13px;
  pointer-events: none;
}

.star-map {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

.star-map--dragging {
  cursor: grabbing;
}

.orbit {
  fill: none;
  stroke: rgba(66, 49, 29, 0.16);
  stroke-width: 1.3;
  stroke-dasharray: 7 10;
}

.orbit--soft {
  opacity: 0.55;
}

.relation {
  fill: none;
  stroke: rgba(35, 31, 24, 0.74);
  stroke-width: 2;
  stroke-linecap: round;
}

.relation--new {
  stroke: #d9ad39;
  stroke-width: 3.2;
  filter: drop-shadow(0 0 5px rgba(217, 173, 57, 0.7));
}

.relation-label {
  fill: rgba(40, 34, 25, 0.68);
  font-size: 14px;
  paint-order: stroke;
  stroke: rgba(255, 249, 236, 0.9);
  stroke-linejoin: round;
  stroke-width: 4px;
}

.star-shape {
  fill: #fff2b9;
  stroke: #251f17;
  stroke-width: 2;
  filter: drop-shadow(0 0 6px rgba(217, 173, 57, 0.42));
}

.star-ray {
  stroke: #251f17;
  stroke-linecap: round;
  stroke-width: 1.8;
}

.node-label {
  fill: #251f17;
  font-size: 21px;
  font-weight: 700;
  paint-order: stroke;
  stroke: rgba(255, 249, 236, 0.84);
  stroke-linejoin: round;
  stroke-width: 5px;
}

.node--dead {
  opacity: 0.45;
}

.tag {
  display: inline-flex;
  align-items: center;
  height: 30px;
  padding: 0 12px;
  border: 1px solid rgba(141, 103, 42, 0.35);
  border-radius: 7px;
  background: rgba(215, 177, 94, 0.22);
  font-weight: 700;
}

.detail-names {
  margin: 22px 0 14px;
  text-align: center;
  font-size: 24px;
  font-weight: 700;
}

.character-detail-list {
  display: grid;
  gap: 12px;
  margin-top: 20px;
}

.character-detail-row {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 42px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(72, 55, 33, 0.12);
}

.character-detail-row__label {
  color: #2d261b;
  font-size: 14px;
  font-weight: 700;
}

.character-detail-row__value {
  overflow: hidden;
  color: rgba(43, 37, 28, 0.74);
  font-size: 14px;
  line-height: 1.6;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: help;
}

.divider {
  height: 1px;
  margin: 14px 0;
  background: linear-gradient(90deg, transparent, rgba(72, 55, 33, 0.26), transparent);
}

.summary-block {
  margin-bottom: 4px;
}

.summary-block .section-title {
  margin-top: 0;
}

.relation-dimension {
  display: grid;
  grid-template-columns: 54px minmax(0, 1fr);
  gap: 12px;
  align-items: center;
  min-height: 66px;
  padding: 8px 0;
  border-bottom: 1px solid rgba(72, 55, 33, 0.12);
}

.relation-dimension:last-child {
  border-bottom: 0;
  margin-bottom: 2px;
  padding-bottom: 2px;
}

.relation-dimension__title {
  color: #2d261b;
  font-size: 16px;
  font-weight: 700;
}

.relation-dimension__body {
  min-width: 0;
}

.relation-dimension__body--energy {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.energy-side {
  min-width: 0;
  text-align: center;
}

.energy-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 58px;
  height: 26px;
  margin: 0 0 8px;
  padding: 0 10px;
  border: 1px solid rgba(92, 124, 77, 0.28);
  border-radius: 8px;
  background: rgba(219, 227, 205, 0.6);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.4);
  color: #49623d;
  font-size: 15px;
  font-weight: 700;
}

.energy-label--empty {
  border-color: rgba(103, 95, 82, 0.22);
  background: rgba(225, 218, 204, 0.42);
  color: rgba(86, 78, 66, 0.62);
}

.energy-cells {
  display: grid;
  gap: 3px;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.energy-cells--attitude {
  grid-template-columns: repeat(8, minmax(10px, 1fr));
}

.energy-cells i {
  display: block;
  height: 15px;
  border: 1px solid rgba(93, 79, 55, 0.2);
  border-radius: 5px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.46), rgba(255, 255, 255, 0) 46%),
    rgba(211, 203, 187, 0.46);
  box-shadow: inset 0 0 0 1px rgba(255, 251, 241, 0.56);
  clip-path: polygon(12% 0, 88% 0, 100% 50%, 88% 100%, 12% 100%, 0 50%);
}

.energy-cells--attitude i.is-active {
  border-color: rgba(68, 96, 55, 0.38);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.48), rgba(255, 255, 255, 0) 42%),
    linear-gradient(180deg, #8aa37a, #536d47);
  box-shadow:
    inset 0 0 0 1px rgba(245, 250, 232, 0.5),
    0 0 8px rgba(90, 118, 69, 0.28);
}

.relation-text-pair,
.relation-bond {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  color: #2d2922;
  font-size: 20px;
  font-weight: 700;
}

.relation-text-pair {
  gap: 12px;
}

.relation-text-pair i {
  flex: 0 0 44px;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(120, 91, 45, 0.44), transparent);
}

.relation-bond {
  color: #6c4f1b;
}

@media (max-width: 1280px) {
  .chapter-panel {
    width: 300px;
  }

  .detail-panel {
    width: 420px;
  }
}
</style>
