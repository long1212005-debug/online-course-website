package com.soa.api_gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder routes) {
        return routes.routes()
                .route("user-service-direct", r -> r
                        .path("/api/v1/auth", "/api/v1/auth/**", "/api/v1/users", "/api/v1/users/**")
                        .uri("http://user-service:8081"))
                .route("course-service-api-direct", r -> r
                        .path("/api/v1/courses", "/api/v1/courses/**",
                                "/api/v1/banners", "/api/v1/banners/**",
                                "/api/v1/videos", "/api/v1/videos/**",
                                "/api/v1/exercises", "/api/v1/exercises/**")
                        .uri("http://course-service:8082"))
                .route("course-service-static-direct", r -> r
                        .path("/images/**", "/hls/**", "/exercises/**")
                        .uri("http://course-service:8082"))
                .route("payment-service-direct", r -> r
                        .path("/api/v1/payments", "/api/v1/payments/**", "/api/v1/wallet", "/api/v1/wallet/**")
                        .uri("http://payment-service:8083"))
                .route("enrollment-service-direct", r -> r
                        .path("/api/v1/enrollments", "/api/v1/enrollments/**")
                        .uri("http://enrollment-service:8084"))
                .route("notification-service-direct", r -> r
                        .path("/api/v1/notifications", "/api/v1/notifications/**", "/ws", "/ws/**")
                        .uri("http://notification-service:8085"))
                .build();
    }
}
