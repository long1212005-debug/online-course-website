package com.soa.course_service.service.impl;

import com.soa.course_service.config.UploadStorage;
import com.soa.course_service.dto.VideoResponseDTO;
import com.soa.course_service.entity.Course;
import com.soa.course_service.entity.Section;
import com.soa.course_service.entity.Video;
import com.soa.course_service.repository.CourseRepository;
import com.soa.course_service.repository.VideoRepository;
import com.soa.course_service.service.HlsService;
import com.soa.course_service.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;
    private final CourseRepository courseRepository;
    private final HlsService hlsService;
    private final UploadStorage uploadStorage;

    // SỬA: Đổi tham số duration từ Long thành Integer để khớp với Controller và
    // Entity
    @Override
    @Transactional
    public VideoResponseDTO addVideoToCourse(Long courseId, Long sectionId, String title, MultipartFile file,
            Integer duration, String teacherEmail) throws IOException { // [Fix] Changed Long to Integer

        Course course = checkTeacherOwnership(courseId, teacherEmail);

        if (file.isEmpty())
            throw new RuntimeException("File rỗng!");

        // Lưu file tạm
        Path tempMp4Path = saveTempFile(file);
        String fileNameWithoutExt = tempMp4Path.getFileName().toString().replace(".mp4", "");

        // Tìm/Tạo Section
        Section targetSection = null;
        if (sectionId != null) {
            targetSection = course.getSections().stream()
                    .filter(s -> s.getId().equals(sectionId)).findFirst().orElse(null);
        }
        if (targetSection == null) {
            if (course.getSections().isEmpty()) {
                targetSection = new Section();
                targetSection.setTitle("Chương 1");
                targetSection.setCourse(course);
                targetSection.setOrderIndex(1);
                course.getSections().add(targetSection);
            } else {
                targetSection = course.getSections().get(0);
            }
        }

        Video video = new Video();
        video.setCourse(course);
        video.setSection(targetSection);
        video.setTitle(title);
        // SỬA: duration đã là Integer, không cần gọi .intValue() nữa (hoặc check null
        // an toàn)
        video.setDurationInSeconds(duration != null ? duration : 0);
        video.setVideoUrl("/uploads/temp/" + tempMp4Path.getFileName().toString());
        video.setStatus("PROCESSING");

        courseRepository.save(course);
        Video savedVideo = videoRepository.save(video);

        try {
            hlsService.processVideoAsync(savedVideo.getId(), tempMp4Path, fileNameWithoutExt);
        } catch (Exception e) {
            System.err.println("Lỗi gọi async: " + e.getMessage());
        }

        return convertToResponseDTO(savedVideo);
    }

    // ... Giữ nguyên các hàm khác (deleteVideo, getVideosForCourse,
    // checkTeacherOwnership, v.v.) ...

    @Override
    public void deleteVideo(Long videoId, String teacherEmail) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video không tồn tại"));
        checkTeacherOwnership(video.getCourse().getId(), teacherEmail);
        videoRepository.delete(video);
    }

    @Override
    public List<VideoResponseDTO> getVideosForCourse(Long courseId, String userEmail, String userRole) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Khóa học không tồn tại"));
        return videoRepository.findByCourse(course).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    // Thêm hàm này nếu interface yêu cầu, nhưng nên bỏ đi nếu không dùng
    @Override
    public VideoResponseDTO addVideoToCourse(Long courseId, Long sectionId, String title, MultipartFile file,
            Long duration, String teacherEmail) throws IOException {
        // Chuyển tiếp sang hàm Integer nếu interface cũ vẫn gọi Long
        return addVideoToCourse(courseId, sectionId, title, file, duration != null ? duration.intValue() : 0,
                teacherEmail);
    }

    public List<VideoResponseDTO> getVideosByCourseId(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Khóa học không tồn tại"));
        return videoRepository.findByCourse(course).stream()
                .map(this::convertToResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public VideoResponseDTO getVideoById(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Video không tồn tại"));
        return convertToResponseDTO(video);
    }

    @Override
    public VideoResponseDTO uploadVideo(MultipartFile file, Long courseId, String teacherEmail) throws IOException {
        // ... (Giữ nguyên logic hàm uploadVideo cũ của bạn) ...
        Course course = checkTeacherOwnership(courseId, teacherEmail);

        if (file.isEmpty())
            throw new RuntimeException("File rỗng!");

        try {
            Path tempMp4Path = saveTempFile(file);
            String fileNameWithoutExt = tempMp4Path.getFileName().toString().replace(".mp4", "");

            Section targetSection;
            if (course.getSections().isEmpty()) {
                targetSection = new Section();
                targetSection.setTitle("Chương 1");
                targetSection.setCourse(course);
                targetSection.setOrderIndex(1);
                course.getSections().add(targetSection);
            } else {
                targetSection = course.getSections().get(0);
            }

            Video video = new Video();
            video.setCourse(course);
            video.setSection(targetSection);
            video.setTitle(file.getOriginalFilename());
            video.setDurationInSeconds(0);
            video.setVideoUrl("/uploads/temp/" + tempMp4Path.getFileName().toString());
            video.setStatus("PROCESSING");

            courseRepository.save(course);
            Video savedVideo = videoRepository.save(video);

            try {
                hlsService.processVideoAsync(savedVideo.getId(), tempMp4Path, fileNameWithoutExt);
            } catch (Exception e) {
                System.err.println("Lỗi gọi async: " + e.getMessage());
            }

            return convertToResponseDTO(savedVideo);
        } catch (IOException e) {
            throw new RuntimeException("Lỗi lưu file: " + e.getMessage(), e);
        }
    }

    private Course checkTeacherOwnership(Long courseId, String teacherEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Khóa học không tồn tại"));
        if (!course.getTeacherEmail().equals(teacherEmail)) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa.");
        }
        return course;
    }

    private Path saveTempFile(MultipartFile file) throws IOException {
        String uniqueFileName = UUID.randomUUID().toString();
        Path tempDir = uploadStorage.resolve("temp");
        if (!Files.exists(tempDir))
            Files.createDirectories(tempDir);
        Path tempFilePath = tempDir.resolve(uniqueFileName + ".mp4");
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, tempFilePath, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFilePath;
    }

    private VideoResponseDTO convertToResponseDTO(Video video) {
        VideoResponseDTO dto = new VideoResponseDTO();
        dto.setId(video.getId());
        dto.setTitle(video.getTitle());
        dto.setVideoUrl(video.getVideoUrl());
        dto.setDurationInSeconds(video.getDurationInSeconds());
        dto.setStatus(video.getStatus());
        if (video.getCourse() != null)
            dto.setCourseId(video.getCourse().getId());
        return dto;
    }
}
