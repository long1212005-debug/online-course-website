import { useEffect, useState } from "react";
import axiosClient from "../../api/axiosClient";
import { Link } from "react-router-dom";

const MyCourses = () => {
  const [enrollments, setEnrollments] = useState([]);

  useEffect(() => {
    axiosClient
      .get("/enrollments/my-courses")
      .then((res) => {
        const myCourses = Array.isArray(res.data?.data)
          ? res.data.data
          : Array.isArray(res.data)
          ? res.data
          : [];
        setEnrollments(myCourses);
      })
      .catch((err) => console.error(err));
  }, []);

  // Hàm xử lý đường dẫn ảnh
  const getImageUrl = (url) => {
    if (!url) return null;
    // Nếu ảnh đã có http (ảnh online) thì giữ nguyên, nếu không thì nối với cổng Gateway 8080
    return url.startsWith("http") ? url : `http://localhost:8080${url}`;
  };

  return (
    <div className="max-w-7xl mx-auto px-6 py-10">
      <h1 className="text-3xl font-bold text-gray-900 mb-8">
        Quá trình học tập của tôi
      </h1>

      <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
        {enrollments.map((item) => (
          <Link
            to={`/learn/${item.courseId}`} // Chuyển thẳng vào trang học
            key={item.id}
            className="block group"
          >
            <div className="bg-white border border-gray-200 rounded-xl overflow-hidden hover:shadow-lg transition-shadow">
              {/* --- LOGIC HIỂN THỊ ẢNH --- */}
              {item.imageUrl ? (
                <div className="h-32 overflow-hidden">
                  <img
                    src={getImageUrl(item.imageUrl)}
                    alt={item.courseTitle}
                    className="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
                    onError={(e) => {
                      // Nếu ảnh lỗi (404), ẩn ảnh đi và hiện fallback bên dưới
                      e.target.style.display = "none";
                      e.target.nextSibling.style.display = "flex";
                    }}
                  />
                  {/* Fallback: Nếu ảnh lỗi thì hiện chữ cái (ẩn mặc định) */}
                  <div className="hidden h-full w-full bg-gray-200 items-center justify-center text-gray-500 font-bold text-xl">
                    {item.courseTitle.charAt(0).toUpperCase()}
                  </div>
                </div>
              ) : (
                // Nếu không có imageUrl thì hiện chữ cái mặc định
                <div className="h-32 bg-gradient-to-r from-purple-500 to-indigo-500 flex items-center justify-center text-white font-bold text-xl">
                  {item.courseTitle.charAt(0).toUpperCase()}
                </div>
              )}
              {/* -------------------------- */}

              <div className="p-4">
                <h3 className="font-bold text-gray-900 line-clamp-2 group-hover:text-purple-600 h-12">
                  {item.courseTitle}
                </h3>
                <p className="text-xs text-gray-500 mt-2">
                  Đã đăng ký: {new Date(item.enrolledAt).toLocaleDateString()}
                </p>

                <div className="w-full bg-gray-200 rounded-full h-2 mt-4">
                  <div
                    className="bg-purple-600 h-2 rounded-full"
                    style={{ width: "30%" }}
                  ></div>
                </div>
                <p className="text-xs text-right text-gray-500 mt-1">
                  30% hoàn thành
                </p>
              </div>
            </div>
          </Link>
        ))}
      </div>

      {enrollments.length === 0 && (
        <div className="text-center py-20">
          <p className="text-gray-500 text-lg">
            Bạn chưa đăng ký khóa học nào.
          </p>
          <Link
            to="/"
            className="text-purple-600 font-bold hover:underline mt-2 inline-block"
          >
            Khám phá ngay
          </Link>
        </div>
      )}
    </div>
  );
};

export default MyCourses;
