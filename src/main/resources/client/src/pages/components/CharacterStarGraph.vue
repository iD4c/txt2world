<template>
  <svg ref="svgRef" class="character-star-graph" role="img" aria-label="人物关系星图"></svg>
</template>

<script setup>
import {nextTick, onBeforeUnmount, onMounted, ref, watch} from "vue";
import * as d3 from "d3";

const props = defineProps({
  graphData: {
    type: Object,
    required: true
  },
  selectedNodeId: {
    type: String,
    default: ''
  },
  selectedLinkId: {
    type: String,
    default: ''
  }
});

const emit = defineEmits(['select-node', 'select-link', 'clear-selection']);

const svgRef = ref(null);
const width = 860;
const height = 640;
let simulation = null;
let root = null;
let linkPath = null;
let linkHitPath = null;
let linkLabel = null;
let nodeGroup = null;
let linkLayer = null;
let hitLayer = null;
let labelLayer = null;
let nodeLayer = null;

function renderGraph() {
  if (!svgRef.value) {
    return;
  }

  stopSimulation();

  const svg = d3.select(svgRef.value);
  const previousPositions = new Map();
  if (nodeGroup) {
    nodeGroup.each(d => {
      previousPositions.set(d.id, {
        x: d.x,
        y: d.y,
        fx: d.fx,
        fy: d.fy
      });
    });
  }

  ensureGraphShell(svg);

  const nodes = (props.graphData.nodes || []).map(node => ({
    ...node,
    x: node.isGraphCenter ? width / 2 : previousPositions.get(node.id)?.x ?? node.x ?? width / 2 + (Math.random() - 0.5) * 180,
    y: node.isGraphCenter ? height / 2 : previousPositions.get(node.id)?.y ?? node.y ?? height / 2 + (Math.random() - 0.5) * 120,
    fx: node.isGraphCenter ? width / 2 : previousPositions.get(node.id)?.fx,
    fy: node.isGraphCenter ? height / 2 : previousPositions.get(node.id)?.fy
  }));
  const links = (props.graphData.links || []).map(link => ({...link}));

  linkPath = linkLayer.selectAll('path.link-visible')
    .data(links, d => d.id)
    .join(
      enter => enter.append('path')
        .attr('class', d => linkClass(d))
        .attr('data-link-id', d => d.id)
        .style('opacity', 0)
        .on('click', (event, d) => {
          event.stopPropagation();
          emit('select-link', d);
        })
        .call(enterSelection => enterSelection.transition().duration(260).style('opacity', 1)),
      update => update.attr('class', d => linkClass(d)),
      exit => exit.transition().duration(320).style('opacity', 0).remove()
    );

  linkHitPath = hitLayer.selectAll('path.link-hit')
    .data(links, d => d.id)
    .join(
      enter => enter.append('path')
        .attr('class', 'link-hit')
        .on('click', (event, d) => {
          event.stopPropagation();
          emit('select-link', d);
        }),
      update => update.attr('class', 'link-hit'),
      exit => exit.remove()
    );

  linkLabel = labelLayer.selectAll('text.link-label')
    .data(links, d => d.id)
    .join(
      enter => enter.append('text')
        .attr('class', 'link-label')
        .attr('text-anchor', 'middle')
        .style('opacity', 0)
        .text(d => d.label || '')
        .call(enterSelection => enterSelection.transition().duration(260).style('opacity', 1)),
      update => update.attr('class', 'link-label').text(d => d.label || ''),
      exit => exit.transition().duration(260).style('opacity', 0).remove()
    );

  nodeGroup = nodeLayer.selectAll('g.star-node')
    .data(nodes, d => d.id)
    .join(enter => {
      const g = enter.append('g')
        .attr('class', d => nodeClass(d))
        .attr('transform', d => `translate(${d.x},${d.y})`)
        .style('opacity', 0)
        .style('cursor', d => d.isGraphCenter ? 'pointer' : 'grab')
        .on('click', (event, d) => {
          event.stopPropagation();
          emit('select-node', d);
        })
        .call(nodeDrag());

      const body = g.append('g')
        .attr('class', 'node-body')
        .attr('transform', 'scale(0.8)');

      body.append('circle')
        .attr('class', 'star-glow')
        .attr('r', d => nodeVisualSize(d).glowRadius);

      body.selectAll('line.star-ray')
        .data(d => starRays(nodeVisualSize(d)))
        .join('line')
        .attr('class', 'star-ray')
        .attr('x1', d => d[0])
        .attr('y1', d => d[1])
        .attr('x2', d => d[2])
        .attr('y2', d => d[3]);

      body.append('path')
        .attr('class', 'star-shape')
        .attr('d', d => {
          const size = nodeVisualSize(d);
          return starPath(size.outerRadius, size.innerRadius);
        });

      body.append('text')
        .attr('class', 'node-label')
        .attr('text-anchor', 'middle')
        .attr('y', d => nodeVisualSize(d).labelY)
        .text(d => d.name);

      g.transition()
        .duration(420)
        .style('opacity', 1);

      body.transition()
        .duration(420)
        .ease(d3.easeBackOut.overshoot(1.2))
        .attr('transform', 'scale(1)');

      return g;
    }, update => {
      update
        .attr('class', d => nodeClass(d))
        .attr('transform', d => `translate(${d.x},${d.y})`)
        .style('cursor', d => d.isGraphCenter ? 'pointer' : 'grab')
        .on('click', (event, d) => {
          event.stopPropagation();
          emit('select-node', d);
        })
        .call(nodeDrag());

      update.select('.node-label')
        .attr('y', d => nodeVisualSize(d).labelY)
        .text(d => d.name);

      update.select('.star-glow')
        .attr('r', d => nodeVisualSize(d).glowRadius);

      update.select('.star-shape')
        .attr('d', d => {
          const size = nodeVisualSize(d);
          return starPath(size.outerRadius, size.innerRadius);
        });

      update.selectAll('line.star-ray')
        .data(d => starRays(nodeVisualSize(d)))
        .attr('x1', d => d[0])
        .attr('y1', d => d[1])
        .attr('x2', d => d[2])
        .attr('y2', d => d[3]);

      return update;
    }, exit => {
      exit.select('.node-body')
        .transition()
        .duration(320)
        .attr('transform', 'scale(0.72)');

      return exit.transition()
        .duration(320)
        .style('opacity', 0)
        .remove();
    });

  simulation = d3.forceSimulation(nodes)
    .force('link', d3.forceLink(links).id(d => d.id).distance(d => d.isNewInCurrentChunk ? 205 : 185).strength(0.62))
    .force('charge', d3.forceManyBody().strength(-560))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force('collide', d3.forceCollide().radius(d => nodeVisualSize(d).collideRadius).strength(0.9))
    .force('x', d3.forceX(width / 2).strength(0.035))
    .force('y', d3.forceY(height / 2).strength(0.035))
    .on('tick', ticked);

  simulation.tick(1);
  ticked();

  nextTick(() => {
    window.setTimeout(animateLinks, 120);
  });

  updateSelection();
}

function ensureGraphShell(svg) {
  svg.attr('viewBox', `0 0 ${width} ${height}`);
  if (root) {
    return;
  }

  svg.selectAll('*').remove();

  const defs = svg.append('defs');
  const glow = defs.append('filter')
    .attr('id', 'star-node-glow')
    .attr('x', '-80%')
    .attr('y', '-80%')
    .attr('width', '260%')
    .attr('height', '260%');
  glow.append('feGaussianBlur').attr('stdDeviation', 4).attr('result', 'blur');
  glow.append('feColorMatrix')
    .attr('in', 'blur')
    .attr('type', 'matrix')
    .attr('values', '1 0 0 0 0.65 0 1 0 0 0.44 0 0 1 0 0.12 0 0 0 0.65 0')
    .attr('result', 'goldGlow');
  const merge = glow.append('feMerge');
  merge.append('feMergeNode').attr('in', 'goldGlow');
  merge.append('feMergeNode').attr('in', 'SourceGraphic');

  root = svg.append('g').attr('class', 'graph-root');

  svg.call(
    d3.zoom()
      .scaleExtent([0.45, 2.8])
      .on('zoom', event => {
        root.attr('transform', event.transform);
      })
  ).on('dblclick.zoom', null);

  svg.on('click', event => {
    if (event.target === svg.node()) {
      emit('clear-selection');
    }
  });

  drawOrbits();

  linkLayer = root.append('g').attr('class', 'link-layer');
  hitLayer = root.append('g').attr('class', 'hit-layer');
  labelLayer = root.append('g').attr('class', 'label-layer');
  nodeLayer = root.append('g').attr('class', 'node-layer');
}

function drawOrbits() {
  const orbitLayer = root.append('g').attr('class', 'orbit-layer');
  [
    {rx: 330, ry: 238},
    {rx: 252, ry: 172},
    {rx: 168, ry: 112}
  ].forEach(orbit => {
    orbitLayer.append('ellipse')
      .attr('class', 'orbit')
      .attr('cx', width / 2)
      .attr('cy', height / 2)
      .attr('rx', orbit.rx)
      .attr('ry', orbit.ry);
  });
}

function ticked() {
  linkPath.attr('d', linkPathD);
  linkHitPath.attr('d', linkPathD);
  linkLabel
    .attr('x', d => ((d.source.x || 0) + (d.target.x || 0)) / 2)
    .attr('y', d => ((d.source.y || 0) + (d.target.y || 0)) / 2 - 8);
  nodeGroup.attr('transform', d => `translate(${d.x},${d.y})`);
}

function linkPathD(d) {
  const sx = d.source.x || 0;
  const sy = d.source.y || 0;
  const tx = d.target.x || 0;
  const ty = d.target.y || 0;
  const dx = tx - sx;
  const dy = ty - sy;
  const len = Math.sqrt(dx * dx + dy * dy) || 1;
  const curve = d.isNewInCurrentChunk ? 28 : 16;
  const mx = (sx + tx) / 2;
  const my = (sy + ty) / 2;
  const cx = mx - dy / len * curve;
  const cy = my + dx / len * curve;
  return `M ${sx} ${sy} Q ${cx} ${cy} ${tx} ${ty}`;
}

function animateLinks() {
  if (!linkPath) {
    return;
  }

  linkPath.filter(d => d.isNewInCurrentChunk).each(function() {
    if (!this.getAttribute('d')) {
      return;
    }
    const path = d3.select(this);
    const length = this.getTotalLength();
    if (!length) {
      return;
    }
    path
      .attr('stroke-dasharray', `${length} ${length}`)
      .attr('stroke-dashoffset', length)
      .transition()
      .duration(760)
      .ease(d3.easeCubicOut)
      .attr('stroke-dashoffset', 0)
      .on('end', function() {
        d3.select(this)
          .attr('stroke-dasharray', null)
          .attr('stroke-dashoffset', null);
      });
  });
}

function nodeDrag() {
  return d3.drag()
    .on('start', function(event, d) {
      event.sourceEvent.stopPropagation();
      if (d.isGraphCenter) {
        d.fx = width / 2;
        d.fy = height / 2;
        return;
      }
      if (!event.active) {
        simulation.alphaTarget(0.24).restart();
      }
      d.fx = d.x;
      d.fy = d.y;
      d3.select(this).style('cursor', 'grabbing');
    })
    .on('drag', (event, d) => {
      event.sourceEvent.stopPropagation();
      if (d.isGraphCenter) {
        d.fx = width / 2;
        d.fy = height / 2;
        return;
      }
      d.fx = event.x;
      d.fy = event.y;
    })
    .on('end', function(event, d) {
      event.sourceEvent.stopPropagation();
      if (d.isGraphCenter) {
        d.fx = width / 2;
        d.fy = height / 2;
        d3.select(this).style('cursor', 'pointer');
        return;
      }
      if (!event.active) {
        simulation.alphaTarget(0);
      }
      d.fx = event.x;
      d.fy = event.y;
      d3.select(this).style('cursor', 'grab');
    });
}

function updateSelection() {
  if (nodeGroup) {
    nodeGroup.attr('class', d => nodeClass(d));
  }
  if (linkPath) {
    linkPath.attr('class', d => linkClass(d));
  }
}

function nodeClass(d) {
  return [
    'star-node',
    `star-node--${nodeTier(d)}`,
    isInactiveLifeStatus(d.lifeStatus) ? 'star-node--inactive' : '',
    props.selectedNodeId === d.id ? 'star-node--selected' : ''
  ].filter(Boolean).join(' ');
}

function linkClass(d) {
  return [
    'link-visible',
    d.isNewInCurrentChunk ? 'link-visible--new' : '',
    props.selectedLinkId === d.id ? 'link-visible--selected' : ''
  ].filter(Boolean).join(' ');
}

function starPath(outer = 23, inner = 8, points = 8) {
  const coords = [];
  for (let index = 0; index < points * 2; index++) {
    const angle = -Math.PI / 2 + index * Math.PI / points;
    const isOuterPoint = index % 2 === 0;
    const outerPointIndex = index / 2;
    const isDiagonalOuterPoint = isOuterPoint && outerPointIndex % 2 === 1;
    const radius = isOuterPoint
      ? outer * (isDiagonalOuterPoint ? 0.72 : 1)
      : inner;
    coords.push(`${Math.cos(angle) * radius},${Math.sin(angle) * radius}`);
  }
  return `M ${coords.join(' L ')} Z`;
}

function nodeTier(d) {
  if (['CORE', 'HIGH'].includes(d.importanceLevel) || d.importance === 'core') {
    return 'major';
  }
  if (d.importanceLevel === 'MEDIUM') {
    return 'medium';
  }
  return 'minor';
}

function nodeVisualSize(d) {
  const tier = nodeTier(d);
  if (tier === 'major') {
    return {
      glowRadius: 36,
      outerRadius: 32,
      innerRadius: 10,
      rayStart: 36,
      rayEnd: 50,
      labelY: 64,
      collideRadius: 86
    };
  }
  if (tier === 'medium') {
    return {
      glowRadius: 30,
      outerRadius: 26,
      innerRadius: 8.5,
      rayStart: 30,
      rayEnd: 43,
      labelY: 56,
      collideRadius: 72
    };
  }
  return {
    glowRadius: 24,
    outerRadius: 21,
    innerRadius: 7,
    rayStart: 25,
    rayEnd: 35,
    labelY: 48,
    collideRadius: 60
  };
}

function isInactiveLifeStatus(lifeStatus) {
  return lifeStatus === '已死亡';
}

function starRays(size) {
  const start = size.rayStart;
  const end = size.rayEnd;
  return [
    [0, -end, 0, -start],
    [0, start, 0, end],
    [-end, 0, -start, 0],
    [start, 0, end, 0]
  ];
}

function stopSimulation() {
  if (simulation) {
    simulation.stop();
    simulation = null;
  }
}

watch(
  () => props.graphData,
  () => renderGraph(),
  {deep: true}
);

watch(
  () => [props.selectedNodeId, props.selectedLinkId],
  () => updateSelection()
);

onMounted(() => {
  renderGraph();
});

onBeforeUnmount(() => {
  stopSimulation();
});
</script>

<style scoped lang="scss">
.character-star-graph {
  display: block;
  width: 100%;
  height: 100%;
  cursor: grab;
  touch-action: none;
  user-select: none;
}

:deep(.orbit) {
  fill: none;
  stroke: rgba(66, 49, 29, 0.16);
  stroke-width: 1.3;
  stroke-dasharray: 7 10;
}

:deep(.link-visible) {
  fill: none;
  stroke: rgba(45, 41, 34, 0.68);
  stroke-linecap: round;
  stroke-width: 2.1;
}

:deep(.link-visible--new),
:deep(.link-visible--selected) {
  stroke: #a77a2d;
  stroke-width: 3.4;
  filter: drop-shadow(0 0 5px rgba(167, 122, 45, 0.56));
}

:deep(.link-hit) {
  fill: none;
  stroke: transparent;
  stroke-linecap: round;
  stroke-width: 18;
  cursor: pointer;
}

:deep(.link-label) {
  fill: rgba(45, 41, 34, 0.72);
  font-size: 14px;
  paint-order: stroke;
  pointer-events: none;
  stroke: rgba(246, 239, 226, 0.92);
  stroke-linejoin: round;
  stroke-width: 4px;
}

:deep(.star-node) {
  color: #2d2922;
}

:deep(.node-body) {
  transition: opacity 0.2s ease, filter 0.2s ease;
}

:deep(.star-glow) {
  fill: rgba(167, 122, 45, 0.18);
  filter: url(#star-node-glow);
}

:deep(.star-ray) {
  stroke: #2d2922;
  stroke-linecap: round;
  stroke-width: 1.8;
}

:deep(.star-shape) {
  fill: #fff1b7;
  stroke: #2d2922;
  stroke-width: 2;
  filter: url(#star-node-glow);
}

:deep(.star-node--major .star-glow) {
  fill: rgba(177, 125, 37, 0.28);
}

:deep(.star-node--major .star-shape) {
  fill: #fff4bf;
  stroke: #2d2922;
  stroke-width: 2.4;
}

:deep(.star-node--major .star-ray) {
  stroke: #2d2922;
  stroke-width: 2;
}

:deep(.star-node--major .node-label) {
  font-size: 22px;
}

:deep(.star-node--medium .star-glow) {
  fill: rgba(166, 125, 63, 0.2);
}

:deep(.star-node--medium .star-shape) {
  fill: #f4e2a8;
  stroke: #493d2b;
  stroke-width: 2;
}

:deep(.star-node--medium .star-ray) {
  stroke: #493d2b;
  stroke-width: 1.7;
}

:deep(.star-node--medium .node-label) {
  font-size: 19px;
}

:deep(.star-node--minor .star-glow) {
  fill: rgba(118, 109, 94, 0.13);
}

:deep(.star-node--minor .star-shape) {
  fill: #e5dcc7;
  stroke: #766d5e;
  stroke-width: 1.7;
}

:deep(.star-node--minor .star-ray) {
  stroke: #766d5e;
  stroke-width: 1.4;
}

:deep(.star-node--minor .node-label) {
  font-size: 16px;
}

:deep(.star-node--inactive .node-body) {
  opacity: 0.48;
  filter: grayscale(0.75) saturate(0.45) brightness(0.84);
}

:deep(.star-node--inactive .node-label) {
  fill: rgba(45, 41, 34, 0.56);
  stroke: rgba(246, 239, 226, 0.72);
}

:deep(.star-node--inactive .star-glow) {
  fill: rgba(90, 83, 72, 0.1);
}

:deep(.star-node--selected .star-shape) {
  stroke: #a77a2d;
  stroke-width: 3;
}

:deep(.star-node--selected .node-body) {
  opacity: 1;
  filter: none;
}

:deep(.star-node--inactive.star-node--selected .node-body) {
  opacity: 0.74;
  filter: grayscale(0.65) saturate(0.52) brightness(0.98);
}

:deep(.star-node--inactive.star-node--selected .star-shape) {
  fill: #d8d0bf;
  stroke: #5f574b;
  stroke-width: 2.6;
}

:deep(.star-node--inactive.star-node--selected .star-ray) {
  stroke: #5f574b;
}

:deep(.star-node--inactive.star-node--selected .star-glow) {
  fill: rgba(112, 103, 89, 0.18);
}

:deep(.node-label) {
  fill: #2d2922;
  font-size: 21px;
  font-weight: 700;
  paint-order: stroke;
  pointer-events: none;
  stroke: rgba(246, 239, 226, 0.88);
  stroke-linejoin: round;
  stroke-width: 5px;
}
</style>
