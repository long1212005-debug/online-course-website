package com.soa.course_service.service;

import com.soa.course_service.config.UploadStorage;
import com.soa.course_service.entity.Video;
import com.soa.course_service.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
@Slf4j
public class HlsService {

    private final VideoRepository videoRepository;
    private final UploadStorage uploadStorage;

    @Async
    public void processVideoAsync(Long videoId, Path inputFilePath, String fileNameWithoutExt) {
        log.info(">>> BẮT ĐẦU XỬ LÝ VIDEO NGẦM ID: {}", videoId);

        try {
            if (!Files.exists(inputFilePath)) {
                log.error("❌ LỖI: Không tìm thấy file đầu vào tại {}", inputFilePath);
                updateStatus(videoId, "FAILED");
                return;
            }

            String absoluteInputPath = inputFilePath.toAbsolutePath().toString();

            // 🔥 BƯỚC 1: LẤY THỜI LƯỢNG THẬT CỦA VIDEO BẰNG FFPROBE
            int duration = getVideoDurationInSeconds(absoluteInputPath);
            log.info(">>> Thời lượng video ID {}: {} giây", videoId, duration);

            // 🔥 BƯỚC 2: CẮT HLS (Giữ nguyên code cũ)
            Path outputFolderPath = uploadStorage.resolve("hls").resolve(fileNameWithoutExt);
            if (!Files.exists(outputFolderPath)) {
                Files.createDirectories(outputFolderPath);
            }

            String absoluteOutputPath = outputFolderPath.resolve("index.m3u8").toAbsolutePath().toString();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", absoluteInputPath,
                    "-codec:", "copy",
                    "-start_number", "0",
                    "-hls_time", "10",
                    "-hls_list_size", "0",
                    "-f", "hls",
                    absoluteOutputPath);

            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("✅ CẮT VIDEO THÀNH CÔNG! ID: {}", videoId);
                String hlsUrl = "/hls/" + fileNameWithoutExt + "/index.m3u8";

                // 🔥 BƯỚC 3: CẬP NHẬT URL VÀ THỜI LƯỢNG VÀO DB
                updateVideoSuccess(videoId, hlsUrl, duration);

                Files.deleteIfExists(inputFilePath);
            } else {
                log.error("❌ FFmpeg thất bại ID: {}", videoId);
                updateStatus(videoId, "FAILED");
            }

        } catch (Exception e) {
            log.error("❌ LỖI JAVA EXCEPTION:", e);
            updateStatus(videoId, "FAILED");
        }
    }

    // --- HÀM PHỤ TRỢ MỚI ---

    // Dùng ffprobe để lấy duration (trả về giây)
    private int getVideoDurationInSeconds(String inputPath) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-show_entries", "format=duration",
                    "-of", "default=noprint_wrappers=1:nokey=1", inputPath);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    double durationDouble = Double.parseDouble(line);
                    return (int) Math.round(durationDouble);
                }
            }
        } catch (Exception e) {
            log.error("Không thể lấy thời lượng video: {}", e.getMessage());
        }
        return 0; // Mặc định nếu lỗi
    }

    private void updateStatus(Long videoId, String status) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video != null) {
            video.setStatus(status);
            videoRepository.save(video);
        }
    }

    // Hàm update mới: Status + URL + Duration
    private void updateVideoSuccess(Long videoId, String url, int duration) {
        Video video = videoRepository.findById(videoId).orElse(null);
        if (video != null) {
            video.setStatus("READY");
            video.setVideoUrl(url);
            if (duration > 0) {
                video.setDurationInSeconds(duration); // Lưu thời lượng thật
            }
            videoRepository.save(video);
        }
    }
}
