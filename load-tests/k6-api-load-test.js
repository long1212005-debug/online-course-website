/**
 * ============================================================================
 * KỊCH BẢN KIỂM THỬ HIỆU NĂNG VÀ TẢI (LOAD TESTING) VỚI K6
 * ============================================================================
 * Dự án: Hệ thống Website Khóa học trực tuyến (Online Course System - Microservices)
 * Mục đích:
 *  - Đo lường độ trễ (latency), thông lượng (throughput) và tỷ lệ lỗi (error rate)
 *  - Đánh giá khả năng chịu tải của API Gateway và các Service: User, Course, Payment
 *  - Hỗ trợ nhiều profile kiểm thử khác nhau: smoke, capacity, quick peak (500 - 5000 VUs)
 * ============================================================================
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";

// --- Cấu hình địa chỉ API Gateway hoặc Host kiểm thử ---
const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

// --- Dữ liệu mẫu phục vụ kiểm thử luồng thanh toán ---
const COURSE_ID = Number(__ENV.COURSE_ID || 1);
const TEACHER_ID = Number(__ENV.TEACHER_ID || 1);
const TEACHER_EMAIL = __ENV.TEACHER_EMAIL || "teacher.loadtest@example.com";

// --- Profile kiểm thử (chọn kịch bản tải qua biến môi trường TEST_PROFILE) ---
const TEST_PROFILE = (__ENV.TEST_PROFILE || "default").toLowerCase();

// --- Cờ bật/tắt các nhóm API cần kiểm thử ---
const RUN_PAYMENT_WRITE_TEST =
  (__ENV.RUN_PAYMENT_WRITE_TEST || "false").toLowerCase() === "true";

const INCLUDE_PUBLIC_APIS =
  (__ENV.INCLUDE_PUBLIC_APIS || "true").toLowerCase() === "true";

const INCLUDE_AUTH_APIS =
  (__ENV.INCLUDE_AUTH_APIS || "true").toLowerCase() === "true";

const INCLUDE_PAYMENT_CREATE =
  (__ENV.INCLUDE_PAYMENT_CREATE || "true").toLowerCase() === "true";

// Thời gian nghỉ giữa các iteration của mỗi Virtual User (giây)
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

// --- Các ngưỡng đảm bảo SLA (Service Level Agreement) ---
const P95_THRESHOLD = Number(__ENV.P95_THRESHOLD || 1500); // 95% request phải phản hồi dưới 1500ms
const ERROR_RATE_THRESHOLD = Number(__ENV.ERROR_RATE_THRESHOLD || 0.05); // Tỷ lệ lỗi tối đa cho phép < 5%

// Custom metric: Đo tỷ lệ thất bại của các bước kiểm tra (check failure rate)
const apiFailureRate = new Rate("api_failure_rate");

/**
 * Xây dựng danh sách giai đoạn tăng/giảm Virtual Users (ramping stages)
 * tùy thuộc vào cấu hình profile kiểm thử được chọn.
 *
 * @returns {Array<{duration: string, target: number}>} Danh sách stages cho k6
 */
function buildStages() {
  // Test rất nhanh 500 VUs
  if (TEST_PROFILE === "quick500") {
    return [
      { duration: "10s", target: 100 },
      { duration: "10s", target: 300 },
      { duration: "30s", target: 500 },
      { duration: "10s", target: 0 },
    ];
  }

  // Test rất nhanh 1000 VUs
  if (TEST_PROFILE === "quick1000") {
    return [
      { duration: "10s", target: 200 },
      { duration: "10s", target: 500 },
      { duration: "30s", target: 1000 },
      { duration: "10s", target: 0 },
    ];
  }

  // Test rất nhanh 2000 VUs
  if (TEST_PROFILE === "quick2000") {
    return [
      { duration: "10s", target: 500 },
      { duration: "10s", target: 1000 },
      { duration: "30s", target: 2000 },
      { duration: "10s", target: 0 },
    ];
  }
  if (TEST_PROFILE === "quick3000") {
    return [
      { duration: "10s", target: 500 },
      { duration: "10s", target: 1000 },
      { duration: "10s", target: 2000 },
      { duration: "30s", target: 3000 },
      { duration: "10s", target: 0 },
    ];
  }
  // Test nhanh 5000 VUs
  if (TEST_PROFILE === "quick5000") {
    return [
      { duration: "10s", target: 1000 },
      { duration: "10s", target: 2000 },
      { duration: "10s", target: 3000 },
      { duration: "30s", target: 5000 },
      { duration: "10s", target: 0 },
    ];
  }

  // Tìm ngưỡng nhanh: 100 -> 300 -> 500 -> 700 -> 1000
  if (TEST_PROFILE === "quick_capacity") {
    return [
      { duration: "10s", target: 100 },
      { duration: "10s", target: 300 },
      { duration: "10s", target: 500 },
      { duration: "10s", target: 700 },
      { duration: "10s", target: 1000 },
      { duration: "10s", target: 0 },
    ];
  }

  // Test 500 VUs tăng dần, ổn định hơn quick500
  if (TEST_PROFILE === "peak500") {
    return [
      { duration: "30s", target: 50 },
      { duration: "30s", target: 100 },
      { duration: "30s", target: 200 },
      { duration: "30s", target: 300 },
      { duration: "1m", target: 500 },
      { duration: "30s", target: 0 },
    ];
  }

  // Tìm ngưỡng chịu tải kỹ hơn: 100 -> 200 -> 300 -> 400 -> 500
  if (TEST_PROFILE === "capacity") {
    return [
      { duration: "30s", target: 100 },
      { duration: "1m", target: 100 },

      { duration: "30s", target: 200 },
      { duration: "1m", target: 200 },

      { duration: "30s", target: 300 },
      { duration: "1m", target: 300 },

      { duration: "30s", target: 400 },
      { duration: "1m", target: 400 },

      { duration: "30s", target: 500 },
      { duration: "1m", target: 500 },

      { duration: "30s", target: 0 },
    ];
  }

  // Smoke test: chạy nhanh để kiểm tra API sống hay không
  if (TEST_PROFILE === "smoke") {
    return [
      { duration: "10s", target: 5 },
      { duration: "20s", target: 5 },
      { duration: "10s", target: 0 },
    ];
  }

  // Mặc định: giống cấu hình ban đầu 20 -> 50 VUs
  return [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 20 },
    { duration: "30s", target: 50 },
    { duration: "1m", target: 50 },
    { duration: "30s", target: 0 },
  ];
}

/**
 * Xây dựng các tiêu chuẩn chất lượng (thresholds) cho k6.
 * Bắt buộc tỷ lệ lỗi tổng thể và thời gian phản hồi (p95) phải đạt chỉ tiêu.
 *
 * @returns {Record<string, Array<string>>}
 */
function buildThresholds() {
  const thresholds = {
    // Tỷ lệ request HTTP lỗi phải thấp hơn ERROR_RATE_THRESHOLD (mặc định 5%)
    http_req_failed: [`rate<${ERROR_RATE_THRESHOLD}`],
    // Tỷ lệ kiểm tra (assert check) thất bại phải thấp hơn ERROR_RATE_THRESHOLD
    api_failure_rate: [`rate<${ERROR_RATE_THRESHOLD}`],
    // 95% số request toàn hệ thống phải phản hồi nhanh hơn P95_THRESHOLD (1500ms)
    http_req_duration: [`p(95)<${P95_THRESHOLD}`],
  };

  // Thiết lập threshold riêng cho từng endpoint nếu được kích hoạt
  if (INCLUDE_PUBLIC_APIS) {
    thresholds["http_req_duration{endpoint:courses}"] = [`p(95)<${P95_THRESHOLD}`];
    thresholds["http_req_duration{endpoint:banners}"] = [`p(95)<${P95_THRESHOLD}`];

    thresholds["http_req_failed{endpoint:courses}"] = [`rate<${ERROR_RATE_THRESHOLD}`];
    thresholds["http_req_failed{endpoint:banners}"] = [`rate<${ERROR_RATE_THRESHOLD}`];
  }

  if (INCLUDE_AUTH_APIS) {
    thresholds["http_req_duration{endpoint:profile}"] = [`p(95)<${P95_THRESHOLD}`];
    thresholds["http_req_failed{endpoint:profile}"] = [`rate<${ERROR_RATE_THRESHOLD}`];
  }

  if (INCLUDE_PAYMENT_CREATE) {
    thresholds["http_req_duration{endpoint:payment_create}"] = [`p(95)<${P95_THRESHOLD}`];
    thresholds["http_req_failed{endpoint:payment_create}"] = [`rate<${ERROR_RATE_THRESHOLD}`];
  }

  if (RUN_PAYMENT_WRITE_TEST) {
    thresholds["http_req_duration{endpoint:payment_write}"] = [`p(95)<${P95_THRESHOLD}`];
    thresholds["http_req_failed{endpoint:payment_write}"] = [`rate<${ERROR_RATE_THRESHOLD}`];
  }

  return thresholds;
}

/**
 * Cấu hình thực thi k6 (scenarios, stages, thresholds và thống kê)
 */
export const options = {
  scenarios: {
    api_load: {
      executor: "ramping-vus",
      stages: buildStages(),
      gracefulRampDown: "30s",
    },
  },

  thresholds: buildThresholds(),

  // Thống kê phân phối độ trễ chi tiết
  summaryTrendStats: ["avg", "min", "med", "max", "p(90)", "p(95)", "p(99)"],
};

/**
 * Khởi tạo headers và params chung cho request HTTP
 *
 * @param {string|null} token JWT token nếu request cần xác thực
 * @param {string} endpoint Tên tag để phân loại metric trên k6
 * @param {string} responseType Kiểu dữ liệu nhận về ('text' | 'none')
 */
function requestParams(token, endpoint, responseType = "none") {
  const headers = {
    "Content-Type": "application/json",
  };

  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }

  return {
    headers,
    tags: {
      endpoint,
    },
    responseType,
    timeout: "30s",
  };
}

/**
 * Ghi nhận kết quả check vào custom metric apiFailureRate
 */
function recordCheck(result) {
  apiFailureRate.add(!result);
}

/**
 * Parser an toàn cho response JSON để tránh crash script khi gặp response lỗi
 */
function safeJson(response) {
  try {
    return response.json();
  } catch {
    return {};
  }
}

/**
 * Trích xuất token đăng nhập từ response body (hỗ trợ nhiều định dạng trả về)
 */
function getLoginToken(response) {
  const body = safeJson(response);

  return (
    body.token ||
    body.accessToken ||
    body.jwt ||
    body.data?.token ||
    body.data?.accessToken ||
    null
  );
}

/**
 * Kiểm tra xem response tạo thanh toán có chứa URL VNPay hay không
 */
function hasPaymentUrl(response) {
  if (response.status !== 200 || !response.body) {
    return false;
  }

  const body = safeJson(response);

  return Boolean(body.url || body.URL || body.paymentUrl || body.data?.url);
}

/**
 * Giai đoạn setup (chạy 1 lần duy nhất trước khi các Virtual Users bắt đầu):
 * 1. Đăng ký tài khoản học viên ngẫu nhiên
 * 2. Đăng nhập để lấy JWT Bearer token chia sẻ cho các VU
 */
export function setup() {
  const suffix = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;

  const email = `student.loadtest.${suffix}@example.com`;
  const password = "LoadTest@123";

  const registerRes = http.post(
    `${BASE_URL}/api/v1/auth/register/student`,
    JSON.stringify({
      fullName: "Student Load Test",
      email,
      password,
      phoneNumber: "0900000000",
      bio: "k6 load test account",
    }),
    requestParams(null, "setup_register", "text")
  );

  const registerOk = check(registerRes, {
    "setup register student returns 200 or 201": (r) => [200, 201].includes(r.status),
  });

  if (!registerOk) {
    throw new Error(
      `Cannot register load-test user. Status=${registerRes.status}, body=${registerRes.body}`
    );
  }

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({
      email,
      password,
    }),
    requestParams(null, "setup_login", "text")
  );

  const token = getLoginToken(loginRes);

  const loginOk = check(loginRes, {
    "setup login returns 200": (r) => r.status === 200,
    "setup login has token": () => Boolean(token),
  });

  if (!loginOk) {
    throw new Error(
      `Cannot login load-test user. Status=${loginRes.status}, body=${loginRes.body}`
    );
  }

  return {
    token,
    studentEmail: email,
  };
}

/**
 * Hàm thực thi chính (chạy lặp lại cho mỗi Virtual User):
 * Mô phỏng hành vi của người dùng thực tế trên hệ thống website:
 * 1. Xem danh sách khóa học và banner quảng cáo (Public Catalog)
 * 2. Xem thông tin tài khoản cá nhân (Authenticated Profile)
 * 3. Tạo link thanh toán khóa học qua VNPay (Payment Gateway)
 * 4. Mô phỏng webhook/callback thanh toán thành công (Payment Write Simulation)
 */
export default function (data) {
  // --- Nhóm 1: Kiểm thử các API công khai (Public Catalog APIs) ---
  if (INCLUDE_PUBLIC_APIS) {
    group("public catalog APIs", () => {
      // 1.1 Lấy danh sách khóa học
      const coursesRes = http.get(
        `${BASE_URL}/api/v1/courses`,
        requestParams(null, "courses", "none")
      );

      recordCheck(
        check(coursesRes, {
          "GET /courses is 200": (r) => r.status === 200,
        })
      );

      // 1.2 Lấy danh sách banner đang hoạt động
      const bannerRes = http.get(
        `${BASE_URL}/api/v1/banners/active`,
        requestParams(null, "banners", "none")
      );

      recordCheck(
        check(bannerRes, {
          "GET /banners/active is 200 or 204": (r) =>
            [200, 204].includes(r.status),
        })
      );
    });
  }

  // --- Nhóm 2: Kiểm thử các API yêu cầu xác thực JWT (User Service) ---
  if (INCLUDE_AUTH_APIS) {
    group("authenticated user APIs", () => {
      // 2.1 Lấy hồ sơ người dùng đăng nhập hiện tại
      const profileRes = http.get(
        `${BASE_URL}/api/v1/users/profile`,
        requestParams(data.token, "profile", "none")
      );

      recordCheck(
        check(profileRes, {
          "GET /users/profile is 200": (r) => r.status === 200,
        })
      );
    });
  }

  // --- Nhóm 3: Kiểm thử API tạo URL thanh toán VNPay (Payment Service) ---
  if (INCLUDE_PAYMENT_CREATE) {
    group("payment create URL API", () => {
      const query =
        `amount=100000` +
        `&courseId=${COURSE_ID}` +
        `&courseTitle=${encodeURIComponent("Load Test Course")}` +
        `&email=${encodeURIComponent(data.studentEmail)}` +
        `&teacherEmail=${encodeURIComponent(TEACHER_EMAIL)}` +
        `&teacherId=${TEACHER_ID}`;

      // 3.1 Khởi tạo yêu cầu thanh toán
      const paymentRes = http.get(
        `${BASE_URL}/api/v1/payments/create_payment?${query}`,
        requestParams(data.token, "payment_create", "text")
      );

      recordCheck(
        check(paymentRes, {
          "GET /payments/create_payment is 200": (r) => r.status === 200,
          "payment response has URL": hasPaymentUrl,
        })
      );
    });
  }

  // --- Nhóm 4: Mô phỏng ghi nhận thanh toán thành công (Test Mode) ---
  if (RUN_PAYMENT_WRITE_TEST) {
    group("payment write simulation API", () => {
      const body =
        `amount=100000` +
        `&courseId=${COURSE_ID}` +
        `&courseTitle=${encodeURIComponent("Load Test Course")}` +
        `&email=${encodeURIComponent(data.studentEmail)}` +
        `&teacherEmail=${encodeURIComponent(TEACHER_EMAIL)}` +
        `&teacherId=${TEACHER_ID}`;

      const writeRes = http.post(
        `${BASE_URL}/api/v1/payments/test/success?${body}`,
        null,
        requestParams(data.token, "payment_write", "text")
      );

      recordCheck(
        check(writeRes, {
          "POST /payments/test/success is 200": (r) => r.status === 200,
        })
      );
    });
  }

  // Nghỉ giữa các lượt thực thi để điều hòa tải (Pacing)
  sleep(SLEEP_SECONDS);
}