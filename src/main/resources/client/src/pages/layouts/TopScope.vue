<template>

  <v-overlay
      :model-value="global_visibleLoading"
      class="align-center justify-center"
      persistent
  >
    <v-progress-circular
        v-if="global_visibleLoading"
        color="primary"
        size="64"
        indeterminate
    ></v-progress-circular>
  </v-overlay>

  <v-snackbar
      v-model="visibleSnackbar"
      location="top right"
      :timeout="1400"
  >
    <v-icon class="mr-2">mdi-check-circle</v-icon>
    {{ snackbarText }}
    <template v-slot:actions>
      <v-btn
          color="blue"
          variant="text"
          @click="visibleSnackbar = false"
      >
        Close
      </v-btn>
    </template>
  </v-snackbar>

  <v-dialog v-model="visibleConfirm" max-width="500">
    <v-card title="信息" prepend-icon="mdi-information">
      <v-card-text>
        {{ confirmText }}
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
            text="取消"
            @click="visibleConfirm = false"
        ></v-btn>
        <v-btn
            color="primary"
            text="确定"
            @click="confirmYes"
        ></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <v-dialog v-model="visibleAwareInfo" max-width="500">
    <v-card title="信息" prepend-icon="mdi-information">
      <v-card-text>
        {{ awareInfoText }}
      </v-card-text>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn
            color="primary"
            text="确定"
            @click="visibleAwareInfo = false"
        ></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

</template>

<script setup>
import {computed, onMounted, ref} from "vue";
import {getCurrentTimestamp} from "@/global/commonFun.js";

/**
 * 变量
 */
const global_visibleLoading = computed(() => {
  return loadingIdSet.value.size !== 0;
});

const visibleSnackbar = ref(false);
const snackbarText = ref('');

const visibleConfirm = ref(false);
const confirmText = ref('');

const visibleAwareInfo = ref(false);
const awareInfoText = ref(false);

const loadingIdSet = ref(new Set());
let confirmYesCb;

/**
 * 方法
 */
function confirmYes() {
  if (confirmYesCb) {
    confirmYesCb();
  }
  visibleConfirm.value = false;
}

/**
 * window挂载
 */
window.global_showSnackbar = (text) => {
  visibleSnackbar.value = true;
  snackbarText.value = text;
}

window.global_showConfirm = (text, yesCb) => {
  visibleConfirm.value = true;
  confirmText.value = text;
  confirmYesCb = yesCb;
}

window.global_showAwareInfo = (text) => {
  visibleAwareInfo.value = true;
  awareInfoText.value = text;
}

window.global_showLoading = () => {
  let loadingId = getCurrentTimestamp();
  loadingIdSet.value.add(loadingId);
  return loadingId;
}

window.global_stopLoading = (loadingId) => {
  loadingIdSet.value.delete(loadingId);
}

onMounted(() => {
});
</script>

<style scoped lang="scss">

</style>