import {defineConfig} from 'vite';
import vue from '@vitejs/plugin-vue';
import vuetify from 'vite-plugin-vuetify';
import path from 'path'; // 需要引入 path 模块
import {createRequire} from 'module';

import legacy from '@vitejs/plugin-legacy'

const require = createRequire(import.meta.url);

export default defineConfig({
  build: {
    chunkSizeWarningLimit: 2000,
    rollupOptions: {
      output: {}
    }
  },
  plugins: [
    vue(),
    vuetify({autoImport: true}),
    legacy({
      targets: ['defaults', 'chrome 85'],  //需要兼容的目标列表，可以设置多个
      additionalLegacyPolyfills: ['regenerator-runtime/runtime']
    })
  ],
  css: {
    postcss: {
      plugins: [
        require('postcss-preset-env')({
          browsers: ['last 2 versions', 'chrome >= 85'],  // 兼容 Chrome 85 及以上
        }),
      ],
    },
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')  // 设置 @ 为 src 目录的别名
    }
  },
  server: {
    port: 49500,
    open: true,
    watch: {
      ignored: ['**/.idea/**']
    }
  }
});
