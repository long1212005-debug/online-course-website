import axios from "axios";

/**
 * ============================================================================
 * AXIOS HTTP CLIENT INSTANCE
 * ============================================================================
 * Cấu hình HTTP Client tập trung cho toàn bộ ứng dụng Frontend:
 * - Tự động gắn tiền tố baseURL theo biến môi trường (mặc định /api/v1).
 * - Đính kèm JWT Authorization header cho mọi request khi đã đăng nhập.
 * - Chuẩn hóa và làm sạch URL nhằm tránh trùng lặp tiền tố /api/v1/api/v1.
 * - Đón bắt và log lỗi HTTP tập trung tại response interceptor.
 * ============================================================================
 */

const axiosClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "/api/v1",
  headers: {
    "Content-Type": "application/json",
  },
  timeout: 30000, // Timeout sau 30 giây nếu server không phản hồi
});

/**
 * ============================================================================
 * REQUEST INTERCEPTOR
 * ============================================================================
 * Chạy trước mỗi HTTP Request được gửi đi:
 * 1. Lấy token xác thực từ localStorage và đưa vào header "Authorization: Bearer <token>"
 * 2. Tự động kiểm tra và cắt bỏ tiền tố trùng lặp nếu lập trình viên gọi nhầm "/api/v1/..."
 */
axiosClient.interceptors.request.use(
  (config) => {
    // 1. Trích xuất access token từ bộ nhớ trình duyệt
    const token = localStorage.getItem("token");

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // 2. Chuẩn hóa endpoint: Nếu đường dẫn gọi có sẵn "/api/v1/", loại bỏ để tránh lặp với baseURL
    // Ví dụ: axiosClient.get("/api/v1/payments") -> gửi tới "/api/v1/payments" thay vì "/api/v1/api/v1/payments"
    if (config.url && config.url.startsWith("/api/v1/")) {
      config.url = config.url.replace(/^\/api\/v1/, "");
    }

    return config;
  },
  (error) => {
    // Xử lý lỗi trước khi request được gửi ra mạng
    return Promise.reject(error);
  }
);

/**
 * ============================================================================
 * RESPONSE INTERCEPTOR
 * ============================================================================
 * Chạy ngay khi nhận được HTTP Response từ Backend:
 * - Trả về response nguyên vẹn nếu thành công (HTTP 2xx).
 * - Đón bắt các mã lỗi HTTP (401 Unauthorized, 403 Forbidden, 500 Internal Error)
 *   giúp ghi log và dễ dàng mở rộng xử lý tự động logout hoặc refresh token.
 */
axiosClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Ghi nhận lỗi API vào console phục vụ debug
    if (error.response) {
      console.error(
        `[API ERROR] ${error.response.status} - ${error.config?.url}:`,
        error.response.data
      );
    } else {
      console.error("[API NETWORK ERROR]:", error.message);
    }
    return Promise.reject(error);
  }
);

export default axiosClient;

