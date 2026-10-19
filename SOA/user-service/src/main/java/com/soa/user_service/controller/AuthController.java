package com.soa.user_service.controller;

import com.soa.user_service.dto.ForgotPasswordRequestDTO;
import com.soa.user_service.dto.LoginRequestDTO;
import com.soa.user_service.dto.LoginResponseDTO;
import com.soa.user_service.dto.RegisterRequestDTO;
import com.soa.user_service.dto.ResetPasswordRequestDTO;
import com.soa.user_service.dto.UserResponseDTO;
import com.soa.user_service.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller phụ trách các API xác thực và quản lý tài khoản người dùng
 * Endpoint cơ sở: /api/v1/auth
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * Đăng ký tài khoản mới với vai trò Học viên (ROLE_STUDENT)
     *
     * @param registerRequest Thông tin đăng ký (họ tên, email, mật khẩu, số điện thoại)
     * @return UserResponseDTO Thông tin người dùng vừa được tạo kèm HTTP 201 Created
     */
    @PostMapping("/register/student")
    public ResponseEntity<UserResponseDTO> registerStudent(@RequestBody RegisterRequestDTO registerRequest) {
        UserResponseDTO response = authService.registerStudent(registerRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Đăng ký tài khoản mới với vai trò Giảng viên (ROLE_TEACHER)
     *
     * @param registerRequest Thông tin đăng ký giảng viên
     * @return UserResponseDTO Thông tin tài khoản kèm HTTP 201 Created
     */
    @PostMapping("/register/teacher")
    public ResponseEntity<UserResponseDTO> registerTeacher(@RequestBody RegisterRequestDTO registerRequest) {
        UserResponseDTO response = authService.registerTeacher(registerRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Đăng nhập hệ thống bằng Email và Mật khẩu
     *
     * @param loginRequest Email và password của người dùng
     * @return LoginResponseDTO Chứa chuỗi JWT Access Token, roles và thông tin cơ bản
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> authenticateUser(@RequestBody LoginRequestDTO loginRequest) {
        LoginResponseDTO loginResponse = authService.loginUser(loginRequest);
        return new ResponseEntity<>(loginResponse, HttpStatus.OK);
    }

    /**
     * Tiếp nhận yêu cầu quên mật khẩu và phát sinh email xác thực
     * Sử dụng phản hồi trung lập nhằm phòng chống kỹ thuật tấn công User Enumeration
     *
     * @param request DTO chứa địa chỉ email cần lấy lại mật khẩu
     * @return Chuỗi thông báo kết quả xử lý an toàn
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        // Gọi service xử lý tạo reset token và gửi link đặt lại mật khẩu qua email
        authService.forgotPassword(request.getEmail());

        // Luôn trả về thông báo thành công dù email có tồn tại hay không (chống User Enumeration)
        return ResponseEntity.ok("Nếu email tồn tại, chúng tôi đã gửi hướng dẫn khôi phục mật khẩu.");
    }

    /**
     * Xác thực reset token và cập nhật mật khẩu mới cho người dùng
     *
     * @param request DTO chứa reset token và new password
     * @return Thông báo trạng thái đặt lại mật khẩu thành công
     */
    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok("Đặt lại mật khẩu thành công!");
    }

}