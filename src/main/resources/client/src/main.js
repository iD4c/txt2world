import {createApp} from 'vue'
import App from './App.vue'
import router from './router.js'

import {createVuetify} from 'vuetify'
import 'vuetify/styles';
import 'vuetify/dist/vuetify.css'

import '@mdi/font/css/materialdesignicons.css'
import {aliases, mdi} from 'vuetify/iconsets/mdi'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'

import '@/assets/css/global.scss'

import {createPinia} from "pinia";


// 创建 Vuetify 实例
const vuetify = createVuetify({
  components,
  directives,
  icons: {
    defaultSet: 'mdi',
    aliases,
    sets: {
      mdi,
    },
  }
});

const app = createApp(App);

const pinia = createPinia();

app.use(pinia);
app.use(router);
app.use(vuetify);

app.mount('#app');
