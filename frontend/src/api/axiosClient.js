import axios from "axios";

// ============================
// CẤU HÌNH AXIOS CLIENT
// ============================

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
});

// ============================
// INTERCEPTOR REQUEST
// - Gắn token
// - Chống lỗi lặp /api/v1/api/v1
// ============================

axiosClient.interceptors.request.use((config) => {
  const token = localStorage.getItem("token");

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  // CHỐNG LỖI LẶP api/v1/api/v1
  // Ví dụ: nếu bạn gọi axiosClient.get("/api/v1/payments/...")
  // nó sẽ tự đổi thành "/payments/..."
  if (config.url.startsWith("/api/v1/")) {
    config.url = config.url.replace("/api/v1", "");
  }

  return config;
});

// ============================
// INTERCEPTOR RESPONSE (optional / bạn có thể giữ hoặc xoá)
// ============================

axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    console.error("API ERROR:", error);
    return Promise.reject(error);
  }
);

export default axiosClient;
