import axiosClient from "@/api/axios-config.js";
import {getPromise} from "@/global/commonFun.js";

export function axiosGetSilent(url, config) {
  return axiosReq(url, null, config, 'get', 'silent');
}

export function axiosGetLoading(url, config) {
  return axiosReq(url, null, config, 'get', 'loading');
}

export function axiosGetFeedback(url, config) {
  return axiosReq(url, null, config, 'get', 'feedback');
}

export function axiosPostSilent(url, data, config) {
  return axiosReq(url, data, config, 'post', 'silent');
}

export function axiosPostLoading(url, data, config) {
  return axiosReq(url, data, config, 'post', 'loading');
}

export function axiosPostFeedback(url, data, config) {
  return axiosReq(url, data, config, 'post', 'feedback');
}

function axiosReq(url, data, config, method, mode) {

  let [promise, resolve] = getPromise();

  let loadingId;
  if (mode === 'loading' || mode === 'feedback') {
    loadingId = window.global_showLoading();
  }

  if (method === 'get') {
    axiosClient.get(url, config)
      .then(response => {
        resCodeProc(response);
        resolve(response.data);
      })
      .catch(err => {
        resErrProc(err);
        resolve(null);
      })
      .finally(() => {
        finallyProc(mode, loadingId);
      });

    return promise;
  }

  if (method === 'post') {
    axiosClient.post(url, data, config)
      .then(response => {
        resCodeProc(response);
        resolve(response.data);
      })
      .catch(err => {
        resErrProc(err);
        resolve(null);
      })
      .finally(() => {
        finallyProc(mode, loadingId);
      });

    return promise;
  }
}

function finallyProc(mode, loadingId) {
  if (mode === 'silent') {
    return;
  }

  if (mode === 'loading' || mode === 'feedback') {
    window.global_stopLoading(loadingId);
  }

  if (mode === 'feedback') {
    window.global_showSnackbar('操作成功！');
  }
}

function resCodeProc(response) {
  if (!response.data) {
    return;
  }

  var code = response.data.code;

  if (code === 2000) {
    return;
  }

  if (code === 2001) {
    window.global_showAwareInfo(response.data.msg);
    return;
  }

  if (code === 2002) {
    window.global_showSnackbar(response.data.msg);
    return;
  }
}

function resErrProc(err) {
  if (!err) {
    return;
  }

  if (err.code === 'ERR_NETWORK') {
    window.global_showAwareInfo('网络异常！');
    return;
  }
}