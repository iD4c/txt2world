import axios from 'axios';

let axiosClient = axios.create({
  baseURL: import.meta.env.VITE_BASE_URL,
  timeout: 60000, // 请求超时时间
  headers: {
    'Content-Type': 'application/json' // 设置默认 Content-Type
  }
});

// 添加请求拦截器
axiosClient.interceptors.request.use(
  (config) => {
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

axiosClient.interceptors.response.use(
  (response) => {
    return response;
  },
  (error) => {
    if (error) {
      console.log(error);
    }
    return Promise.reject(error);
  }
);

export default axiosClient;
