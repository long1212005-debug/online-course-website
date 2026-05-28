import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const COURSE_ID = Number(__ENV.COURSE_ID || 1);
const TEACHER_ID = Number(__ENV.TEACHER_ID || 1);
const TEACHER_EMAIL = __ENV.TEACHER_EMAIL || "teacher.loadtest@example.com";
const RUN_PAYMENT_WRITE_TEST = (__ENV.RUN_PAYMENT_WRITE_TEST || "false").toLowerCase() === "true";

const apiFailureRate = new Rate("api_failure_rate");

function hasPaymentUrl(response) {
  if (response.status !== 200 || !response.body) {
    return false;
  }

  try {
    const body = response.json();
    return Boolean(body.url || body.URL);
  } catch {
    return false;
  }
}

export const options = {
  scenarios: {
    api_load: {
      executor: "ramping-vus",
      stages: [
        { duration: "30s", target: 20 },
        { duration: "1m", target: 20 },
        { duration: "30s", target: 50 },
        { duration: "1m", target: 50 },
        { duration: "30s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1500"],
    api_failure_rate: ["rate<0.05"],
  },
};

function jsonHeaders(token) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return { headers };
}

function recordCheck(result) {
  apiFailureRate.add(!result);
}

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
    jsonHeaders()
  );

  check(registerRes, {
    "setup register student returns 201": (r) => r.status === 201,
  });

  const loginRes = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    jsonHeaders()
  );

  const loginOk = check(loginRes, {
    "setup login returns 200": (r) => r.status === 200,
    "setup login has token": (r) => Boolean(r.json("token")),
  });

  if (!loginOk) {
    throw new Error(`Cannot login load-test user. Status=${loginRes.status}, body=${loginRes.body}`);
  }

  return {
    token: loginRes.json("token"),
    studentEmail: email,
  };
}

export default function (data) {
  group("public catalog APIs", () => {
    const coursesRes = http.get(`${BASE_URL}/api/v1/courses`);
    recordCheck(check(coursesRes, {
      "GET /courses is 200": (r) => r.status === 200,
    }));

    const bannerRes = http.get(`${BASE_URL}/api/v1/banners/active`);
    recordCheck(check(bannerRes, {
      "GET /banners/active is 200 or 204": (r) => [200, 204].includes(r.status),
    }));
  });

  group("authenticated user APIs", () => {
    const profileRes = http.get(`${BASE_URL}/api/v1/users/profile`, jsonHeaders(data.token));
    recordCheck(check(profileRes, {
      "GET /users/profile is 200": (r) => r.status === 200,
    }));
  });

  group("payment create URL API", () => {
    const query =
      `amount=100000` +
      `&courseId=${COURSE_ID}` +
      `&courseTitle=${encodeURIComponent("Load Test Course")}` +
      `&email=${encodeURIComponent(data.studentEmail)}` +
      `&teacherEmail=${encodeURIComponent(TEACHER_EMAIL)}` +
      `&teacherId=${TEACHER_ID}`;

    const paymentRes = http.get(`${BASE_URL}/api/v1/payments/create_payment?${query}`, jsonHeaders(data.token));
    recordCheck(check(paymentRes, {
      "GET /payments/create_payment is 200": (r) => r.status === 200,
      "payment response has URL": hasPaymentUrl,
    }));
  });

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
        jsonHeaders(data.token)
      );
      recordCheck(check(writeRes, {
        "POST /payments/test/success is 200": (r) => r.status === 200,
      }));
    });
  }

  sleep(1);
}
