<template>
  <section class="wiki-upload-page">
    <div class="wiki-upload-card">
      <div class="wiki-upload-card__header">
        <p class="wiki-upload-card__eyebrow">NEW WIKI PROJECT</p>
        <h1>新增Wiki项目</h1>
      </div>

      <div class="wiki-upload-form">
        <v-text-field
          v-model="projectName"
          label="项目名"
          variant="outlined"
          density="comfortable"
          color="#a77a2d"
          hide-details="auto"
          :disabled="uploading"
        />

        <v-file-input
          v-model="txtFile"
          label="上传小说 txt"
          variant="outlined"
          density="comfortable"
          color="#a77a2d"
          accept=".txt,text/plain"
          prepend-icon=""
          prepend-inner-icon="mdi-file-document-outline"
          hide-details="auto"
          :disabled="uploading"
        />

        <div v-if="errorText" class="paper-alert">
          {{ errorText }}
        </div>

        <div class="wiki-upload-form__actions">
          <v-btn
            color="#a77a2d"
            :loading="uploading"
            :disabled="!canSubmit"
            @click="handleSubmit"
          >
            <v-icon start icon="mdi-upload"/>
            开始解析
          </v-btn>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import {computed, ref} from "vue";
import {axiosPostLoading} from "@/api/axios.js";
import {back} from "@/global/commonBizFun.js";

const projectName = ref('');
const txtFile = ref(null);
const uploading = ref(false);
const errorText = ref('');

const canSubmit = computed(() => Boolean(projectName.value.trim() && normalizeFile(txtFile.value)));

function normalizeFile(value) {
  if (Array.isArray(value)) {
    return value[0] || null;
  }
  return value || null;
}

function handleBack() {
  back();
}

function handleSubmit() {
  uploadProject();
}

async function uploadProject() {
  const file = normalizeFile(txtFile.value);
  errorText.value = '';

  if (!projectName.value.trim()) {
    errorText.value = '请填写项目名';
    return;
  }
  if (!file) {
    errorText.value = '请选择 txt 文件';
    return;
  }

  const formData = new FormData();
  formData.append('projectName', projectName.value.trim());
  formData.append('file', file);

  uploading.value = true;
  const res = await axiosPostLoading('/wiki/upload-and-parse', formData, {
    headers: {'Content-Type': 'multipart/form-data'}
  });
  uploading.value = false;

  if (!res || res.code !== 2000) {
    errorText.value = res?.msg || '上传失败';
    return;
  }

  window.global_showSnackbar?.('项目已创建，后台正在解析');
  back();
}
</script>

<style scoped lang="scss">
.wiki-upload-page {
  min-height: 100%;
  padding: 44px 42px 56px;
  color: #2d2922;
}

.wiki-upload-card {
  max-width: 720px;
  padding: 30px;
  border: 1px solid rgba(92, 72, 39, 0.22);
  border-radius: 8px;
  background: rgba(255, 250, 239, 0.68);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.34), 0 18px 38px rgba(74, 55, 28, 0.08);
  backdrop-filter: blur(5px);
}

.wiki-upload-card__header {
  margin-bottom: 28px;
  padding-bottom: 18px;
  border-bottom: 1px solid rgba(84, 66, 38, 0.16);
}

.wiki-upload-card__eyebrow {
  margin: 0 0 8px;
  font-family: Georgia, serif;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: rgba(78, 64, 43, 0.62);
}

.wiki-upload-card h1 {
  margin: 0;
  font-size: 30px;
  line-height: 1.2;
}

.wiki-upload-form {
  display: grid;
  gap: 18px;
}

.wiki-upload-form__actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 8px;
}

.paper-alert {
  padding: 14px 16px;
  border: 1px solid rgba(126, 52, 41, 0.22);
  border-radius: 8px;
  background: rgba(255, 246, 235, 0.7);
  color: #7e3429;
}

@media (max-width: 720px) {
  .wiki-upload-page {
    padding: 24px 18px 40px;
  }

  .wiki-upload-card {
    padding: 22px;
  }

  .wiki-upload-form__actions {
    flex-direction: column-reverse;
  }
}
</style>
