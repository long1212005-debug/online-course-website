package com.soa.user_service.util;

import com.soa.user_service.security.services.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tiện ích xử lý JSON Web Token (JWT)
 * 
 * Chịu trách nhiệm tạo mới, giải mã trích xuất thông tin người dùng
 * và kiểm tra tính hợp lệ của access token trong toàn bộ hệ thống.
 */
@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    /**
     * Tạo khóa bí mật HMAC-SHA dựa trên chuỗi bí mật (Secret Key).
     * Sử dụng UTF-8 charset để đảm bảo tính đồng bộ chữ ký với các dịch vụ khác (Payment, Gateway).
     * 
     * @return Cryptographic Key cho thuật toán HS256
     */
    private Key key() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Sinh JWT Token khi người dùng đăng nhập thành công.
     * Payload bao gồm:
     * - Subject: Email / Username của người dùng
     * - roles: Danh sách vai trò (ROLE_STUDENT, ROLE_TEACHER, ROLE_ADMIN)
     * - id: User ID trong cơ sở dữ liệu
     * - iat (Issued At): Thời điểm tạo token
     * - exp (Expiration): Thời điểm hết hạn của token
     * 
     * @param authentication Thông tin xác thực người dùng từ Spring Security Context
     * @return Chuỗi JWT đã được ký số bằng khóa bí mật
     */
    public String generateJwtToken(Authentication authentication) {
        UserDetailsImpl userPrincipal = (UserDetailsImpl) authentication.getPrincipal();

        List<String> roles = userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .setSubject(userPrincipal.getUsername())
                .claim("roles", roles)
                .claim("id", userPrincipal.getId())
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Giải mã Token để trích xuất email (Subject) của người dùng
     * 
     * @param token Chuỗi JWT token
     * @return Email của người dùng đã xác thực
     */
    public String getEmailFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    /**
     * Kiểm tra tính hợp lệ của JWT Token (Chữ ký đúng, còn hạn sử dụng, cấu trúc hợp lệ)
     * 
     * @param authToken Chuỗi token cần kiểm tra
     * @return true nếu token hợp lệ, false nếu hết hạn hoặc bị giả mạo
     */
    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (MalformedJwtException e) {
            logger.error("Định dạng JWT không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("JWT token đã hết hạn sử dụng: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Chuỗi claims JWT rỗng: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Lỗi xác thực JWT: {}", e.getMessage());
        }
        return false;
    }
}