package com.soa.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

/**
 * Cấu hình chia sẻ tài nguyên giữa các nguồn gốc (Cross-Origin Resource Sharing - CORS)
 * 
 * Cho phép các ứng dụng Single Page Application (Vite / React) tương tác an toàn
 * với API Gateway mà không bị trình duyệt chặn bởi chính sách Same-Origin Policy.
 */
@Configuration
public class CorsConfig {

    /**
     * Tạo bộ lọc CorsWebFilter cho Spring Cloud Gateway (Reactive WebFlux):
     * - Chỉ định danh sách Origins được phép gửi request.
     * - Khai báo đầy đủ các phương thức HTTP và Headers được chấp nhận.
     * - Hỗ trợ gửi kèm Cookie / Authorization Header (Credentials).
     * - Cache kết quả kiểm tra Preflight request (OPTIONS) để tối ưu hiệu năng mạng.
     * 
     * @return CorsWebFilter áp dụng toàn cục cho mọi đường dẫn (/**)
     */
    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // 1. Cho phép các domain Frontend cụ thể (tránh dùng wildcard '*' khi bật allowCredentials)
        corsConfig.setAllowedOrigins(Arrays.asList(
                "http://localhost:5173", // Frontend Vite dev server
                "http://localhost:3000", // Frontend React dev server
                "http://localhost"       // Nginx reverse proxy hoặc Docker host
        ));

        // 2. Cho phép đầy đủ các HTTP Methods cần thiết cho hoạt động CRUD
        corsConfig.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        // 3. Cho phép các HTTP Headers gửi từ Client (bao gồm JWT token và Content-Type)
        corsConfig.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));

        // 4. Cho phép gửi Token xác thực Bearer / Cookie phiên làm việc
        corsConfig.setAllowCredentials(true);

        // 5. Lưu bộ đệm cấu hình Preflight request (OPTIONS) trong 1 giờ (3600s)
        corsConfig.setMaxAge(3600L);

        // Đăng ký cấu hình CORS cho tất cả các đường dẫn qua Gateway
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}

