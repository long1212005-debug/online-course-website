package com.soa.payment_service.controller;

import com.soa.payment_service.config.VNPayConfig;
import com.soa.payment_service.dto.ChartDataDTO;
import com.soa.payment_service.dto.PaymentDTO;
import com.soa.payment_service.dto.RestResponse;
import com.soa.payment_service.entity.TransactionHistory;
import com.soa.payment_service.repository.TransactionRepository;
import com.soa.payment_service.service.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    private final VNPayConfig vnPayConfig;
    private final RestTemplate restTemplate;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${service.enrollment.url:http://localhost:8084}")
    private String enrollmentServiceUrl;

    @Value("${payment.test-mode.enabled:false}")
    private boolean paymentTestModeEnabled;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private WalletService walletService;

    public PaymentController(VNPayConfig vnPayConfig, RestTemplate restTemplate) {
        this.vnPayConfig = vnPayConfig;
        this.restTemplate = restTemplate;
    }

    @GetMapping("/create_payment")
    public ResponseEntity<PaymentDTO> createPayment(
            HttpServletRequest req,
            @RequestParam("amount") long amount,
            @RequestParam("courseId") Long courseId,
            @RequestParam("courseTitle") String courseTitle,
            @RequestParam("email") String studentEmail,
            @RequestParam("teacherEmail") String teacherEmail,
            @RequestParam("teacherId") Long teacherId) throws IOException {

        logger.info(">>> [VNPAY START] Course ID: {}, Teacher ID: {}", courseId, teacherId);

        String vnpTxnRef = vnPayConfig.getRandomNumber(8);
        String vnpIpAddr = vnPayConfig.getIpAddress(req);
        long amountVal = amount * 100;

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", vnPayConfig.vnp_Version);
        vnpParams.put("vnp_Command", vnPayConfig.vnp_Command);
        vnpParams.put("vnp_TmnCode", vnPayConfig.vnp_TmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amountVal));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_BankCode", "NCB");
        vnpParams.put("vnp_TxnRef", vnpTxnRef);
        vnpParams.put("vnp_OrderInfo", "Thanh toan khoa hoc " + courseId);
        vnpParams.put("vnp_OrderType", vnPayConfig.orderType);
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_IpAddr", vnpIpAddr);

        String returnUrlWithData = vnPayConfig.vnp_ReturnUrl
                + "?courseId=" + courseId
                + "&studentEmail=" + URLEncoder.encode(studentEmail, StandardCharsets.US_ASCII.toString())
                + "&teacherEmail=" + URLEncoder.encode(teacherEmail, StandardCharsets.US_ASCII.toString())
                + "&courseTitle=" + URLEncoder.encode(courseTitle, StandardCharsets.US_ASCII.toString())
                + "&teacherId=" + teacherId;
        vnpParams.put("vnp_ReturnUrl", returnUrlWithData);

        TimeZone vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh");
        Calendar cld = Calendar.getInstance(vnTimeZone);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(vnTimeZone);
        String vnpCreateDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_CreateDate", vnpCreateDate);

        cld.add(Calendar.MINUTE, 15);
        String vnpExpireDate = formatter.format(cld.getTime());
        vnpParams.put("vnp_ExpireDate", vnpExpireDate);

        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isBlank()) {
                hashData.append(fieldName).append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String secureHash = vnPayConfig.hmacSHA512(vnPayConfig.secretKey, hashData.toString());
        String paymentUrl = vnPayConfig.vnp_PayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        logger.info(">>> [VNPAY CREATE] TxnRef: {}, CreateDate: {}, ExpireDate: {}",
                vnpTxnRef, vnpCreateDate, vnpExpireDate);

        PaymentDTO paymentDTO = new PaymentDTO();
        paymentDTO.setStatus("OK");
        paymentDTO.setMessage("Successfully");
        paymentDTO.setURL(paymentUrl);
        return ResponseEntity.ok(paymentDTO);
    }

    @GetMapping("/vnpay-return")
    public void vnpayReturn(
            @RequestParam(value = "vnp_ResponseCode") String responseCode,
            @RequestParam(value = "vnp_Amount") String vnpAmount,
            @RequestParam(value = "vnp_TxnRef") String txnRef,
            @RequestParam("courseId") Long courseId,
            @RequestParam("courseTitle") String courseTitle,
            @RequestParam("studentEmail") String studentEmail,
            @RequestParam("teacherEmail") String teacherEmail,
            @RequestParam("teacherId") Long teacherId,
            HttpServletResponse response) throws IOException {

        if (!"00".equals(responseCode)) {
            response.sendRedirect(frontendUrl + "/payment-failed?code=vnpay_failed");
            return;
        }

        try {
            BigDecimal totalAmount = new BigDecimal(vnpAmount).divide(new BigDecimal(100));
            saveSuccessfulPayment(txnRef, courseId, courseTitle, studentEmail, teacherEmail, teacherId, totalAmount);
            callEnrollmentService(courseId, courseTitle, studentEmail, teacherId);
            response.sendRedirect(frontendUrl + "/payment-success?courseId=" + courseId);
        } catch (Exception e) {
            logger.error("!!! [VNPAY RETURN ERROR]", e);
            response.sendRedirect(frontendUrl + "/payment-failed?code=vnpay_processing_failed");
        }
    }

    @PostMapping("/test/success")
    public ResponseEntity<RestResponse<Map<String, Object>>> simulateSuccessfulPayment(
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("courseId") Long courseId,
            @RequestParam("courseTitle") String courseTitle,
            @RequestParam("email") String studentEmail,
            @RequestParam("teacherEmail") String teacherEmail,
            @RequestParam("teacherId") Long teacherId) {

        if (!paymentTestModeEnabled) {
            return ResponseEntity.status(403)
                    .body(RestResponse.error("Che do test thanh toan dang tat", 403));
        }

        String transactionId = "TEST_" + System.currentTimeMillis();
        saveSuccessfulPayment(transactionId, courseId, courseTitle, studentEmail, teacherEmail, teacherId, amount);
        callEnrollmentService(courseId, courseTitle, studentEmail, teacherId);

        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", transactionId);
        data.put("courseId", courseId);
        data.put("amount", amount);

        return ResponseEntity.ok(RestResponse.success(data, "Gia lap thanh toan thanh cong"));
    }

    private void saveSuccessfulPayment(String transactionId, Long courseId, String courseTitle, String studentEmail,
                                       String teacherEmail, Long teacherId, BigDecimal totalAmount) {
        BigDecimal adminShare = totalAmount.multiply(new BigDecimal("0.40"));
        BigDecimal teacherShare = totalAmount.subtract(adminShare);

        TransactionHistory history = new TransactionHistory();
        history.setTransactionId(transactionId);
        history.setCourseId(courseId);
        history.setCourseTitle(courseTitle);
        history.setStudentEmail(studentEmail);
        history.setTeacherEmail(teacherEmail);
        history.setTeacherId(teacherId);
        history.setTotalAmount(totalAmount);
        history.setAdminCommission(adminShare);
        history.setTeacherReceived(teacherShare);
        transactionRepository.save(history);

        try {
            logger.info(">>> [PAYMENT SUCCESS] Cong vi cho Teacher ID: {}, amount: {}", teacherId, teacherShare);
            walletService.processRevenueShare(teacherId, totalAmount, courseTitle);
        } catch (Exception e) {
            logger.error("!!! [ERROR] Loi cong vi: ", e);
        }
    }

    private void callEnrollmentService(Long courseId, String courseTitle, String email, Long teacherId) {
        String enrollmentUrl = enrollmentServiceUrl + "/api/v1/enrollments/internal/enroll";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("courseId", courseId);
        requestBody.put("courseTitle", courseTitle);
        requestBody.put("studentEmail", email);
        requestBody.put("imageUrl", "default.png");
        requestBody.put("teacherId", teacherId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Internal-Secret", "Ba0MatN0iBo_123456");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        restTemplate.postForObject(enrollmentUrl, entity, String.class);
    }

    @GetMapping("/history")
    public ResponseEntity<List<TransactionHistory>> getAllTransactions() {
        List<TransactionHistory> list = transactionRepository.findAll();
        list.sort((a, b) -> b.getId().compareTo(a.getId()));
        return ResponseEntity.ok(list);
    }

    @GetMapping("/stats/monthly-revenue")
    public ResponseEntity<RestResponse<List<ChartDataDTO>>> getMonthlyRevenue() {
        List<ChartDataDTO> stats = transactionRepository.getMonthlyRevenue();
        return ResponseEntity.ok(RestResponse.success(stats, "Lay doanh thu thang thanh cong"));
    }

    @GetMapping("/stats/dashboard")
    public ResponseEntity<RestResponse<Map<String, Object>>> getDashboardStats() {
        Map<String, Object> data = new HashMap<>();

        List<ChartDataDTO> dailyRevenue = transactionRepository.getDailyRevenue();
        List<ChartDataDTO> topCourses = transactionRepository.getTopSellingCourses()
                .stream().limit(5).collect(Collectors.toList());
        List<TransactionHistory> recentTransactions = transactionRepository.findTop5ByOrderByCreatedAtDesc();

        BigDecimal totalRevenue = dailyRevenue.stream()
                .map(ChartDataDTO::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        data.put("revenueChart", dailyRevenue);
        data.put("topCourses", topCourses);
        data.put("recentTransactions", recentTransactions);
        data.put("totalRevenue", totalRevenue);

        return ResponseEntity.ok(RestResponse.success(data, "Lay Dashboard thanh cong"));
    }
}
