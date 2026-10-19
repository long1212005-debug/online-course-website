package com.soa.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình định tuyến trung tâm (API Gateway Routing Configuration)
 * 
 * Đóng vai trò là Single Entry Point (Điểm truy cập duy nhất) cho toàn bộ hệ thống:
 * - Điều hướng (Route) các HTTP request từ Client tới các microservice tương ứng trong mạng nội bộ Docker.
 * - Tách biệt rõ ràng các API nghiệp vụ (REST endpoints) và các tài nguyên tĩnh (Static Media, HLS Video Streaming).
 */
@Configuration
public class GatewayRoutesConfig {

    /**
     * Định nghĩa bảng định tuyến phân luồng lưu lượng mạng cho từng dịch vụ:
     * 
     * 1. user-service (Port 8081):
     *    - /api/v1/auth/** : Đăng ký, đăng nhập, quên mật khẩu, reset mật khẩu
     *    - /api/v1/users/**: Hồ sơ người dùng, phân quyền, cập nhật thông tin
     * 
     * 2. course-service (Port 8082):
     *    - /api/v1/courses/**  : Quản lý khóa học, bài học (Lesson), chương học (Section)
     *    - /api/v1/banners/**  : Quản lý banner quảng cáo trang chủ
     *    - /api/v1/videos/**   : Quản lý video bài giảng
     *    - /api/v1/exercises/**: Bài tập, câu hỏi trắc nghiệm và nộp bài
     * 
     * 3. course-service-static (Port 8082):
     *    - /images/**   : Ảnh đại diện, ảnh bìa khóa học
     *    - /hls/**      : Phân đoạn video HLS (.m3u8 playlist và file .ts) phục vụ phát video bảo mật
     *    - /exercises/**: Tài liệu, file đính kèm bài tập
     * 
     * 4. payment-service (Port 8083):
     *    - /api/v1/payments/**: Tích hợp cổng thanh toán VNPay (tạo URL, callback IPN, kiểm tra trạng thái)
     *    - /api/v1/wallet/**  : Quản lý số dư ví và biến động số dư của giảng viên/học viên
     * 
     * 5. enrollment-service (Port 8084):
     *    - /api/v1/enrollments/**: Đăng ký tham gia khóa học, kiểm tra điều kiện học, tiến độ học viên
     * 
     * 6. notification-service (Port 8085):
     *    - /api/v1/notifications/**: Truy vấn danh sách thông báo qua REST API
     *    - /ws/**                  : Kết nối WebSocket (STOMP/SockJS) nhận thông báo thời gian thực
     */
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder routes) {
        return routes.routes()
                // --- Tuyến 1: Quản lý tài khoản & Xác thực người dùng ---
                .route("user-service-direct", r -> r
                        .path("/api/v1/auth", "/api/v1/auth/**", "/api/v1/users", "/api/v1/users/**")
                        .uri("http://user-service:8081"))

                // --- Tuyến 2: Quản trị nội dung Khóa học & Bài giảng ---
                .route("course-service-api-direct", r -> r
                        .path("/api/v1/courses", "/api/v1/courses/**",
                                "/api/v1/banners", "/api/v1/banners/**",
                                "/api/v1/videos", "/api/v1/videos/**",
                                "/api/v1/exercises", "/api/v1/exercises/**")
                        .uri("http://course-service:8082"))

                // --- Tuyến 3: Phục vụ tài nguyên tĩnh & Stream video HLS ---
                .route("course-service-static-direct", r -> r
                        .path("/images/**", "/hls/**", "/exercises/**")
                        .uri("http://course-service:8082"))

                // --- Tuyến 4: Cổng thanh toán & Quản lý ví giao dịch ---
                .route("payment-service-direct", r -> r
                        .path("/api/v1/payments", "/api/v1/payments/**", "/api/v1/wallet", "/api/v1/wallet/**")
                        .uri("http://payment-service:8083"))

                // --- Tuyến 5: Ghi danh khóa học & Theo dõi quá trình học ---
                .route("enrollment-service-direct", r -> r
                        .path("/api/v1/enrollments", "/api/v1/enrollments/**")
                        .uri("http://enrollment-service:8084"))

                // --- Tuyến 6: Hệ thống thông báo & WebSocket Real-time ---
                .route("notification-service-direct", r -> r
                        .path("/api/v1/notifications", "/api/v1/notifications/**", "/ws", "/ws/**")
                        .uri("http://notification-service:8085"))
                .build();
    }
}
