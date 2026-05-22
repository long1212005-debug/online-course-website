import React, { useEffect, useState } from "react";
import axiosClient from "../../api/axiosClient";
import AdminHeader from "../../components/admin/AdminHeader";
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
import { Line, Doughnut, Bar } from "react-chartjs-2";

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

const AdminDashboard = () => {
  const [dashboardData, setDashboardData] = useState(null);
  const [userStats, setUserStats] = useState([]);
  const [loading, setLoading] = useState(true);

  // Palette màu hiện đại
  const CHART_COLORS = ["#6366f1", "#10b981", "#f59e0b", "#ef4444", "#8b5cf6"];

  useEffect(() => {
    const fetchData = async () => {
      try {
        const [resDash, resUser] = await Promise.all([
          axiosClient.get("/payments/stats/dashboard"),
          axiosClient.get("/users/stats"),
        ]);
        setDashboardData(resDash.data?.data || resDash.data || {});
        setUserStats(
          Array.isArray(resUser.data?.data)
            ? resUser.data.data
            : Array.isArray(resUser.data)
            ? resUser.data
            : []
        );
      } catch (error) {
        console.error("Lỗi tải dữ liệu:", error);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  const formatCurrency = (amount) =>
    new Intl.NumberFormat("vi-VN", {
      style: "currency",
      currency: "VND",
    }).format(amount || 0);

  const toNumber = (value) => Number(value || 0);
  const revenueChart = Array.isArray(dashboardData?.revenueChart)
    ? dashboardData.revenueChart
    : [];
  const topCourses = Array.isArray(dashboardData?.topCourses)
    ? dashboardData.topCourses
    : [];
  const recentTransactions = Array.isArray(dashboardData?.recentTransactions)
    ? dashboardData.recentTransactions
    : [];
  const totalTopCourseSales = topCourses.reduce(
    (total, item) => total + toNumber(item.value),
    0
  );

  // --- CHART CONFIGS ---
  const lineChartData = {
    labels: revenueChart.map((d) => d.label),
    datasets: [
      {
        label: "Doanh thu",
        data: revenueChart.map((d) => toNumber(d.value)),
        fill: true,
        backgroundColor: (context) => {
          const ctx = context.chart.ctx;
          const gradient = ctx.createLinearGradient(0, 0, 0, 300);
          gradient.addColorStop(0, "rgba(99, 102, 241, 0.2)");
          gradient.addColorStop(1, "rgba(99, 102, 241, 0)");
          return gradient;
        },
        borderColor: "#6366f1",
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 6,
      },
    ],
  };

  const commonOptions = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
  };

  const axisChartOptions = {
    ...commonOptions,
    scales: {
      x: {
        grid: { display: false },
        ticks: { color: "#94a3b8", font: { size: 11 } },
      },
      y: {
        border: { display: false },
        grid: { color: "#f1f5f9" },
        ticks: { color: "#94a3b8", font: { size: 11 } },
      },
    },
  };

  const doughnutOptions = {
    ...commonOptions,
    cutout: "70%",
  };

  const doughnutData = {
    labels: topCourses.map((c) => c.label),
    datasets: [
      {
        data: topCourses.map((c) => toNumber(c.value)),
        backgroundColor: CHART_COLORS,
        borderWidth: 0,
        hoverOffset: 4,
      },
    ],
  };

  const userChartData = {
    labels: userStats.map((d) => d.label),
    datasets: [
      {
        label: "Thành viên",
        data: userStats.map((d) => toNumber(d.value)),
        backgroundColor: "#8b5cf6",
        borderRadius: 4,
        barThickness: 20,
      },
    ],
  };

  if (loading) return <LoadingState />;

  return (
    // 🔥 QUAN TRỌNG: Div bao ngoài này đã được set w-full và max-w-full
    <div className="w-full max-w-full min-h-screen bg-slate-50 p-6 space-y-6">
      <AdminHeader
        title="Tổng quan"
        subtitle="Chào mừng trở lại, đây là báo cáo hôm nay của bạn."
      />

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <KpiCard
          title="Tổng doanh thu"
          value={formatCurrency(dashboardData?.totalRevenue)}
          iconName="dollar"
          color="indigo"
        />
        <KpiCard
          title="Học viên mới"
          value={
            userStats.length > 0
              ? "+" + userStats[userStats.length - 1].value
              : "0"
          }
          iconName="users"
          color="emerald"
        />
        <KpiCard
          title="Tổng khóa học"
          value={dashboardData?.totalCourses || 0}
          iconName="book"
          color="amber"
        />
        <KpiCard
          title="Giao dịch"
          value={recentTransactions.length}
          iconName="cart"
          color="rose"
        />
      </div>

      {/* Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <h3 className="font-bold text-slate-800 text-lg mb-6">
            Biến động doanh thu
          </h3>
          <div className="h-72">
            {revenueChart.length > 0 ? (
              <Line data={lineChartData} options={axisChartOptions} />
            ) : (
              <EmptyChartMessage />
            )}
          </div>
        </div>

        <div className="lg:col-span-1 bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex flex-col">
          <h3 className="font-bold text-slate-800 text-lg mb-6">
            Top khóa học
          </h3>
          <div className="flex-1 flex items-center justify-center relative min-h-[200px]">
            <Doughnut
              data={doughnutData}
              options={doughnutOptions}
            />
            <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
              <span className="text-3xl font-bold text-slate-800">
                {totalTopCourseSales}
              </span>
              <span className="text-xs text-slate-500 uppercase">Đã bán</span>
            </div>
          </div>
          <div className="mt-6 space-y-3">
            {topCourses.slice(0, 4).map((c, i) => (
              <div key={i} className="flex justify-between text-sm">
                <div className="flex items-center gap-2">
                  <span
                    className="w-2.5 h-2.5 rounded-full"
                    style={{ background: CHART_COLORS[i] }}
                  ></span>
                  <span className="text-slate-600 truncate max-w-[150px]">
                    {c.label}
                  </span>
                </div>
                <span className="font-semibold text-slate-800">{c.value}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Bottom Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-1 bg-white p-6 rounded-2xl shadow-sm border border-slate-100">
          <h3 className="font-bold text-slate-800 text-lg mb-6">
            Tăng trưởng người dùng
          </h3>
          <div className="h-60">
            {userStats.length > 0 ? (
              <Bar data={userChartData} options={axisChartOptions} />
            ) : (
              <EmptyChartMessage />
            )}
          </div>
        </div>

        <div className="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-slate-100 overflow-hidden">
          <div className="p-6 border-b border-slate-50 flex justify-between items-center">
            <h3 className="font-bold text-slate-800 text-lg">
              Giao dịch gần đây
            </h3>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
              <thead className="text-xs text-slate-500 uppercase bg-slate-50/50">
                <tr>
                  <th className="px-6 py-4 font-semibold">Học viên</th>
                  <th className="px-6 py-4 font-semibold">Khóa học</th>
                  <th className="px-6 py-4 font-semibold text-right">
                    Số tiền
                  </th>
                  <th className="px-6 py-4 font-semibold text-center">
                    Trạng thái
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-50">
                {recentTransactions.map((tx) => (
                  <tr
                    key={tx.id}
                    className="hover:bg-slate-50/50 transition-colors"
                  >
                    <td className="px-6 py-4 font-medium text-slate-700">
                      {tx.studentEmail}
                    </td>
                    <td className="px-6 py-4 text-slate-600 max-w-xs truncate">
                      {tx.courseTitle}
                    </td>
                    <td className="px-6 py-4 text-right font-bold text-slate-800">
                      {formatCurrency(tx.totalAmount)}
                    </td>
                    <td className="px-6 py-4 text-center">
                      <span className="inline-flex px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-700">
                        Thành công
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};

// --- Sub-components ---

const LoadingState = () => (
  <div className="h-[80vh] flex flex-col items-center justify-center">
    <div className="w-10 h-10 border-4 border-indigo-200 border-t-indigo-600 rounded-full animate-spin mb-4"></div>
    <p className="text-slate-500 font-medium">Đang tải dữ liệu...</p>
  </div>
);

const EmptyChartMessage = () => (
  <div className="h-full min-h-[160px] flex items-center justify-center text-sm text-slate-400">
    Chưa có dữ liệu thống kê
  </div>
);

const KpiCard = ({ title, value, iconName, color }) => {
  const icons = {
    dollar: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z"
      />
    ),
    users: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
      />
    ),
    book: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"
      />
    ),
    cart: (
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M3 3h2l.4 2M7 13h10l4-8H5.4M7 13L5.4 5M7 13l-2.293 2.293c-.63.63-.184 1.707.707 1.707H17m0 0a2 2 0 100 4 2 2 0 000-4zm-8 2a2 2 0 11-4 0 2 2 0 014 0z"
      />
    ),
  };

  const colors = {
    indigo: "text-indigo-600 bg-indigo-50",
    emerald: "text-emerald-600 bg-emerald-50",
    amber: "text-amber-600 bg-amber-50",
    rose: "text-rose-600 bg-rose-50",
  };

  return (
    <div className="bg-white p-6 rounded-2xl shadow-sm border border-slate-100 flex items-center justify-between hover:shadow-md transition-shadow">
      <div>
        <p className="text-slate-500 text-xs font-bold uppercase tracking-wider mb-2">
          {title}
        </p>
        <h4 className="text-2xl font-extrabold text-slate-800">{value}</h4>
      </div>
      <div className={`p-4 rounded-xl ${colors[color]} bg-opacity-60`}>
        <svg
          xmlns="http://www.w3.org/2000/svg"
          className="h-6 w-6"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
          strokeWidth={2}
        >
          {icons[iconName]}
        </svg>
      </div>
    </div>
  );
};

export default AdminDashboard;
