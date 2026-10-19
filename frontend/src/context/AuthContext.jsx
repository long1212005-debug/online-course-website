import React, { createContext, useContext, useState, useEffect } from "react";

/**
 * ============================================================================
 * AUTHENTICATION CONTEXT & PROVIDER
 * ============================================================================
 * Quản lý trạng thái xác thực toàn cục (Global Authentication State):
 * - Lưu trữ thông tin người dùng đang đăng nhập (user profile, roles, email).
 * - Đồng bộ hóa token và thông tin phiên làm việc với localStorage.
 * - Cung cấp các hành vi đăng nhập (login) và đăng xuất (logout) cho toàn ứng dụng.
 * ============================================================================
 */

// 1. Khởi tạo React Context lưu trữ trạng thái đăng nhập
export const AuthContext = createContext(null);

/**
 * Provider bọc ngoài các component cần truy cập trạng thái người dùng
 * 
 * @param {object} props
 * @param {React.ReactNode} props.children
 */
export const AuthProvider = ({ children }) => {
  // Khởi tạo state user ban đầu từ localStorage để giữ phiên đăng nhập khi F5 reload trang
  const [user, setUser] = useState(() => {
    try {
      const savedUser = localStorage.getItem("user");
      return savedUser ? JSON.parse(savedUser) : null;
    } catch (e) {
      console.error("Không thể đọc thông tin người dùng từ localStorage:", e);
      return null;
    }
  });

  /**
   * Lưu thông tin đăng nhập thành công vào state và localStorage
   * 
   * @param {object} userData Dữ liệu trả về từ backend API đăng nhập (chứa token, email, roles...)
   */
  const login = (userData) => {
    setUser(userData);
    localStorage.setItem("user", JSON.stringify(userData));
    if (userData.token) {
      localStorage.setItem("token", userData.token);
    }
  };

  /**
   * Đăng xuất người dùng: Xóa sạch dữ liệu phiên khỏi state và localStorage
   */
  const logout = () => {
    setUser(null);
    localStorage.removeItem("user");
    localStorage.removeItem("token");
  };

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

/**
 * Custom Hook tiện lợi giúp các Component con dễ dàng tiêu thụ AuthContext
 * Thay vì phải import cả useContext và AuthContext ở mỗi file
 * 
 * @returns {{ user: object|null, login: Function, logout: Function }}
 */
export const useAuth = () => {
  return useContext(AuthContext);
};

export default AuthContext;

