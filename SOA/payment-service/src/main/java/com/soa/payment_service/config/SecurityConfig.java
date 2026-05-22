package com.soa.payment_service.config;

import com.soa.payment_service.util.AuthTokenFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthTokenFilter authTokenFilter)
            throws Exception {
        http
                // 1. Tắt CSRF
                .csrf(AbstractHttpConfigurer::disable)

                // 2. 🔥 QUAN TRỌNG: TẮT CORS CỦA SERVICE ĐI (Để Gateway xử lý)
                // Thay vì .cors(cors -> cors.configurationSource(...))
                // Hãy dùng dòng dưới đây:
                .cors(AbstractHttpConfigurer::disable)

                // 3. Quản lý Session: Stateless
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Phân quyền
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/payments/vnpay-return",
                                "/api/v1/payments/ipn",
                                "/api/v1/payments/momo-return",
                                "/api/v1/payments/momo-ipn"
                        ).permitAll()
                        .requestMatchers("/api/v1/wallet/**").authenticated()
                        .anyRequest().authenticated());

        http.addFilterBefore(authTokenFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ❌ XOÁ HOẶC COMMENT LẠI BEAN corsConfigurationSource Ở DƯỚI NẾU CÓ
}
