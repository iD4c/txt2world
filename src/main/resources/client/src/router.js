import {createRouter, createWebHistory} from 'vue-router';
import Layout from "@/pages/Layout.vue";
import WikiProjectManage from "@/pages/wiki/WikiProjectManage.vue";
import WikiProjectUpload from "@/pages/wiki/WikiProjectUpload.vue";
import WikiView from "@/pages/wiki/WikiView.vue";
import WikiViewChooseProject from "@/pages/wiki/WikiViewChooseProject.vue";

export const ROUTER_NAMES = {
  HOME: Symbol('首页'),
  WIKI_PROJECT_MANAGE: Symbol('Wiki项目管理'),
  WIKI_PROJECT_UPLOAD: Symbol('新增Wiki项目'),
  WIKI_VIEW_CHOOSE_PROJECT: Symbol('选择Wiki项目'),
  WIKI_VIEW: Symbol('浏览Wiki'),
};

const routes = [
  {
    path: '/layout',
    name: 'Layout',
    component: Layout,
    children: [
      {
        path: '/home',
        name: ROUTER_NAMES.HOME,
        component: WikiView,
        meta: {breadcrumbs: ['首页']}
      },
      {
        path: '/wiki-view',
        name: ROUTER_NAMES.WIKI_VIEW,
        component: WikiView,
        meta: {breadcrumbs: ['关系图']}
      },
      {
        path: '/wiki-project-manage',
        name: ROUTER_NAMES.WIKI_PROJECT_MANAGE,
        component: WikiProjectManage,
        meta: {breadcrumbs: ['Wiki项目管理']}
      },
      {
        path: '/wiki-project-upload',
        name: ROUTER_NAMES.WIKI_PROJECT_UPLOAD,
        component: WikiProjectUpload,
        meta: {breadcrumbs: ['Wiki项目管理', '新增项目']}
      },
      {
        path: '/wiki-view-choose-project',
        name: ROUTER_NAMES.WIKI_VIEW_CHOOSE_PROJECT,
        component: WikiViewChooseProject,
        meta: {breadcrumbs: ['选择项目']}
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to, from, next) => {

  if (to.path === '/') {
    next('/wiki-view-choose-project');
    return;
  }

  next();
});

export const name2componentMap = new Map(router.getRoutes().map(route => [route.name, route.components.default]));
export const name2metaMap = new Map(router.getRoutes().map(route => [route.name, route.meta]));

export default router;
