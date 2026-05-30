import {name2componentMap, name2metaMap} from "@/router.js";

export class CountDownLatch {
  constructor(count) {
    this.count = count;
    this.promise = new Promise((resolve) => {
      this.resolve = resolve;
    });
  }

  // 每当一个任务完成时，调用这个方法
  countDown() {
    this.count--;
    if (this.count <= 0) {
      this.resolve();
    }
  }

  // 等待所有任务完成
  await() {
    return this.promise;
  }
}

export class Stack {
  constructor(initVal) {
    if (initVal) {
      this.items = initVal;
    } else {
      this.items = [];
    }
  }

  // 入栈 (Push)
  push(element) {
    this.items.push(element);
  }

  // 出栈 (Pop)
  pop() {
    if (this.isEmpty()) {
      return null;
    }
    return this.items.pop();
  }

  // 查看栈顶元素
  peek() {
    if (this.isEmpty()) {
      return null;
    }
    return this.items[this.items.length - 1];
  }

  getSecond() {
    if (!this.items || this.items.length < 2) {
      return null;
    }
    return this.items[this.items.length - 2];
  }

  // 检查栈是否为空
  isEmpty() {
    return !this.items || this.items.length === 0;
  }

  // 获取栈的大小
  size() {
    return this.items ? this.items.length : 0;
  }

  // 清空栈
  clear() {
    this.items = [];
  }

  setOne(element) {
    this.clear();
    this.push(element);
  }
}

export function getListSize(list) {

  if (!list || !list.length) {
    return 0;
  }

  return list.length;
}

export function isEmpty(list) {
  return !list || list.length === 0;
}

export function notEmpty(list) {
  return list && list.length > 0;
}

export function copyToClipboard(text) {

  const tempInput = document.createElement('textarea');
  tempInput.value = text;
  document.body.appendChild(tempInput);
  tempInput.select();
  try {
    document.execCommand('copy');
    window.global_showSnackbar('已复制到剪贴板！');
  } catch (err) {
    window.global_showSnackbar('复制失败，请手动复制！');
  }
  document.body.removeChild(tempInput);
}

export function base64EncodeUnicode(str) {
  const utf8Bytes = new TextEncoder().encode(str);
  const binaryStr = String.fromCharCode(...utf8Bytes);
  return btoa(binaryStr);
}

export function base64DecodeUnicode(base64) {
  const binaryStr = atob(base64);
  const utf8Bytes = Uint8Array.from(binaryStr, char => char.charCodeAt(0));
  return new TextDecoder().decode(utf8Bytes);
}

export function formatDatetime(dateString) {
  const year = dateString.slice(0, 4);
  const month = dateString.slice(4, 6);
  const day = dateString.slice(6, 8);
  const hours = dateString.slice(8, 10);
  const minutes = dateString.slice(10, 12);
  return `${year}-${month}-${day} ${hours}:${minutes}`;
}

export function getCurrentDatetime() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const MM = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const HH = String(now.getHours()).padStart(2, '0');
  const mm = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');

  return `${yyyy}-${MM}-${dd} ${HH}:${mm}:${ss}`;
}

export function getCurrentTimestamp() {
  const now = new Date();
  const yyyy = now.getFullYear();
  const MM = String(now.getMonth() + 1).padStart(2, '0');
  const dd = String(now.getDate()).padStart(2, '0');
  const HH = String(now.getHours()).padStart(2, '0');
  const mm = String(now.getMinutes()).padStart(2, '0');
  const ss = String(now.getSeconds()).padStart(2, '0');
  const SSS = String(now.getMilliseconds()).padStart(3, '0');

  return `${yyyy}${MM}${dd}${HH}${mm}${ss}${SSS}`;
}

export function formatTimestamp(timestamp) {
  const date = new Date(timestamp); // 使用时间戳创建日期对象

  // 获取各个时间部分
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0'); // 月份从 0 开始，补 0
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');

  // 拼接成所需格式
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

export function formatRfc1123Time(originalTime) {
  // 创建一个 Date 对象，解析 RFC 1123 格式的时间
  const date = new Date(originalTime);

  // 获取各个日期和时间组件
  const year = date.getFullYear();
  const month = (date.getMonth() + 1).toString().padStart(2, '0');  // 月份从 0 开始，需要加 1
  const day = date.getDate().toString().padStart(2, '0');
  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');
  const seconds = date.getSeconds().toString().padStart(2, '0');

  // 格式化为 "YYYY-MM-DD HH:mm:ss"
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

export function getFileSuffix(filename) {
  if (!filename) {
    return '';
  }

  const lastIndexOf = filename.lastIndexOf('.');
  if (lastIndexOf === -1) {
    return '';
  }

  return filename.substring(lastIndexOf + 1);
}

export function getMimeType(suffix) {
  const mimeTypeMap = {
    'pdf': 'application/pdf',
    'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'txt': 'text/plain',
    'md': 'text/markdown',
    'jpg': 'image/jpeg',
    'png': 'image/png',
    'jpeg': 'image/jpeg',
    'csv': 'text/csv',
    'eml': 'message/rfc822'
  }
  return mimeTypeMap[suffix] || '';
}


export function calcMb(bytes) {
  return (bytes / 1024 / 1024).toFixed(2);
}


export function getPromise() {
  let rsv;
  let rjt;
  const pms = new Promise((resolve, reject) => {
    rsv = resolve;
    rjt = reject;
  });

  return [pms, rsv, rjt];
}

export function getRouterComponent(name) {
  return name2componentMap.get(name);
}

export function getRouterMeta(name) {
  return name2metaMap.get(name);
}

export function list2Csv(list) {
  let ids = '';
  if (isEmpty(list)) {
    return ids;
  }

  if (typeof list === "string") {
    return list;
  }

  list.forEach(item => {
    ids += item;
    ids += ','
  });

  return ids.slice(0, ids.length - 1);
}

export function csv2List(csv) {
  if (isEmpty(csv)) {
    return [];
  }

  return csv.split(',');
}

export function getMapMaxKey(map) {
  if (!map || map.size === 0) return 0;

  return Math.max(...map.keys());
}