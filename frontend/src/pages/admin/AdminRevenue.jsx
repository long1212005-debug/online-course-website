import { useEffect, useState } from "react";
import axiosClient from "../../api/axiosClient";
import AdminHeader from "../../components/admin/AdminHeader";

// 1. Import các thành phần Chart.js
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler,
} from "chart.js";
import { Line, Doughnut } from "react-chartjs-2";

// 2. Đăng ký Chart.js
ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  BarElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler
);

const AdminRevenue = () => {
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);

  // State lưu dữ liệu cho biểu đồ
  const [chartData, setChartData] = useState({
    dailyLabels: [],
    dailyValues: [],
    adminTotal: 0,
    teacherTotal: 0,
  });

  useEffect(() => {
    fetchRevenueData();
  }, []);

  const fetchRevenueData = async () => {
    try {
      const res = await axiosClient.get("/payments/history");
      const data = Array.isArray(res.data?.data)
        ? res.data.data
        : Array.isArray(res.data)
        ? res.data
        : [];

      // Sắp xếp giao dịch theo ngày cũ nhất -> mới nhất để vẽ biểu đồ line
      const sortedData = [...data].sort(
        (a, b) => new Date(a.createdAt) - new Date(b.createdAt)
      );

      setTransactions([...sortedData].reverse()); // Đảo ngược lại để hiển thị table (Mới nhất lên đầu)

      processChartData(sortedData);
    } catch (err) {
      console.error("Lỗi tải doanh thu:", err);
    } finally {
      setLoading(false);
    }
  };

  // --- HÀM XỬ LÝ DỮ LIỆU CHO BIỂU ĐỒ ---
  const processChartData = (transactions) => {
    const dailyMap = {};
    let totalAdmin = 0;
    let totalTeacher = 0;

    transactions.forEach((t) => {
      const amount = Number(t.totalAmount || 0);

      // 1. Tính tổng chia sẻ (40% Admin - 60% Giáo viên)
      totalAdmin += amount * 0.4;
      totalTeacher += amount * 0.6;

      // 2. Gom nhóm theo ngày (dd/mm)
      const date = new Date(t.createdAt).toLocaleDateString("vi-VN", {
        day: "2-digit",
        month: "2-digit",
      });

      if (dailyMap[date]) {
        dailyMap[date] += amount;
      } else {
        dailyMap[date] = amount;
      }
    });

    setChartData({
      dailyLabels: Object.keys(dailyMap),
      dailyValues: Object.values(dailyMap),
      adminTotal: totalAdmin,
      teacherTotal: totalTeacher,
    });
  };

  // Helper format tiền tệ
  const formatCurrency = (amount) => {
    return new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(amount || 0);
  };

  // --- CẤU HÌNH BIỂU ĐỒ LINE (Doanh thu theo ngày) ---
  const lineChartData = {
    labels: chartData.dailyLabels,
    datasets: [
      {
        label: "Tổng doanh thu ngày",
        data: chartData.dailyValues,
        fill: true,
        backgroundColor: "rgba(99, 102, 241, 0.2)", // Indigo nhạt
        borderColor: "#6366f1", // Indigo đậm
        tension: 0.4,
        pointRadius: 4,
        pointBackgroundColor: "#fff",
        pointBorderColor: "#6366f1",
      },
    ],
  };

  const lineOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        callbacks: {
          label: (context) => formatCurrency(context.raw),
        },
      },
    },
    scales: {
      y: {
        beginAtZero: true,
        grid: { borderDash: [2, 4], color: "#e2e8f0" },
        ticks: { callback: (value) => value / 1000000 + "M" },
      },
      x: { grid: { display: false } },
    },
  };

  // --- CẤU HÌNH BIỂU ĐỒ DOUGHNUT (Phân bổ Doanh thu) ---
  const doughnutData = {
    labels: ["Doanh thu Giáo viên (60%)", "Doanh thu Admin (40%)"],
    datasets: [
      {
        data: [chartData.teacherTotal, chartData.adminTotal],
        backgroundColor: ["#10b981", "#8b5cf6"], // Xanh lá (GV), Tím (Admin)
        borderWidth: 0,
        hoverOffset: 4,
      },
    ],
  };

  if (loading)
    return (
      <div className="p-10 text-center text-gray-500">Đang tải dữ liệu...</div>
    );

  return (
    <div className="space-y-6">
      <AdminHeader
        title="Quản lý Doanh thu"
        subtitle="Theo dõi dòng tiền và lịch sử giao dịch chi tiết"
      />

      {/* --- PHẦN 1: BIỂU ĐỒ --- */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Cột trái: Biểu đồ đường (Biến động theo ngày) */}
        <div className="lg:col-span-2 bg-white p-6 rounded-xl shadow-sm border border-gray-100">
          <div className="flex justify-between items-center mb-4">
            <h3 className="font-bold text-gray-700">
              Biến động doanh thu theo ngày
            </h3>
            <span className="text-xs bg-indigo-50 text-indigo-700 px-2 py-1 rounded font-medium">
              30 ngày gần nhất
            </span>
          </div>
          <div className="h-72">
            {chartData.dailyLabels.length > 0 ? (
              <Line data={lineChartData} options={lineOptions} />
            ) : (
              <div className="h-full flex items-center justify-center text-gray-400">
                Chưa có dữ liệu giao dịch
              </div>
            )}
          </div>
        </div>

        {/* Cột phải: Biểu đồ tròn (Tỷ lệ phân chia) */}
        <div className="lg:col-span-1 bg-white p-6 rounded-xl shadow-sm border border-gray-100 flex flex-col">
          <h3 className="font-bold text-gray-700 mb-6">Phân bổ lợi nhuận</h3>

          <div className="flex-grow flex items-center justify-center relative">
            <div className="w-56 h-56">
              <Doughnut
                data={doughnutData}
                options={{
                  cutout: "70%",
                  plugins: { legend: { display: false } },
                }}
              />
            </div>
            {/* Tổng số tiền ở giữa */}
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span className="text-gray-400 text-xs uppercase font-bold">
                Tổng thu
              </span>
              <span className="text-xl font-extrabold text-gray-800">
                {formatCurrency(chartData.adminTotal + chartData.teacherTotal)}
              </span>
            </div>
          </div>

          {/* Chú thích Legend */}
          <div className="mt-6 space-y-3">
            <div className="flex justify-between items-center p-3 bg-green-50 rounded-lg">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-green-500"></span>
                <span className="text-sm font-medium text-gray-700">
                  Giáo viên
                </span>
              </div>
              <span className="font-bold text-green-700">
                {formatCurrency(chartData.teacherTotal)}
              </span>
            </div>
            <div className="flex justify-between items-center p-3 bg-purple-50 rounded-lg">
              <div className="flex items-center gap-2">
                <span className="w-3 h-3 rounded-full bg-purple-500"></span>
                <span className="text-sm font-medium text-gray-700">
                  Admin (Hệ thống)
                </span>
              </div>
              <span className="font-bold text-purple-700">
                {formatCurrency(chartData.adminTotal)}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* --- PHẦN 2: BẢNG GIAO DỊCH --- */}
      <div className="bg-white rounded-xl shadow-sm border border-gray-200 overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-100 flex justify-between items-center">
          <h3 className="font-bold text-gray-800">
            Lịch sử giao dịch chi tiết
          </h3>
          <button className="text-sm text-indigo-600 font-medium hover:underline">
            Xuất báo cáo
          </button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead className="bg-gray-50 text-gray-500 text-xs uppercase font-semibold">
              <tr>
                <th className="px-6 py-3">Mã GD</th>
                <th className="px-6 py-3">Người mua</th>
                <th className="px-6 py-3">Khóa học</th>
                <th className="px-6 py-3 text-right">Giá trị</th>
                <th className="px-6 py-3 text-right">Phí Admin</th>
                <th className="px-6 py-3 text-center">Ngày giờ</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {transactions.map((t) => (
                <tr key={t.id} className="hover:bg-gray-50 transition">
                  <td className="px-6 py-4 font-mono text-xs text-gray-500">
                    {t.transactionId}
                  </td>
                  <td className="px-6 py-4 text-sm font-medium text-gray-700">
                    {t.studentEmail}
                  </td>
                  <td
                    className="px-6 py-4 text-sm text-gray-600 max-w-[200px] truncate"
                    title={t.courseTitle}
                  >
                    {t.courseTitle}
                  </td>
                  <td className="px-6 py-4 text-right font-bold text-gray-800">
                    {formatCurrency(t.totalAmount)}
                  </td>
                  <td className="px-6 py-4 text-right text-purple-600 font-medium text-sm">
                    +{formatCurrency(t.totalAmount * 0.4)}
                  </td>
                  <td className="px-6 py-4 text-center text-xs text-gray-500">
                    {new Date(t.createdAt).toLocaleString("vi-VN")}
                  </td>
                </tr>
              ))}
              {transactions.length === 0 && (
                <tr>
                  <td
                    colSpan="6"
                    className="px-6 py-10 text-center text-gray-400 italic"
                  >
                    Chưa có dữ liệu giao dịch nào.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default AdminRevenue;
