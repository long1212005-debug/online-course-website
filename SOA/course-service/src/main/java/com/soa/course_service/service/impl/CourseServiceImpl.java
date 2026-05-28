package com.soa.course_service.service.impl;

import com.soa.course_service.config.UploadStorage;
import com.soa.course_service.dto.*;
import com.soa.course_service.entity.*;
import com.soa.course_service.repository.CourseRepository;
import com.soa.course_service.repository.UserLessonProgressRepository;
import com.soa.course_service.repository.VideoRepository;
import com.soa.course_service.service.CourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final VideoRepository videoRepository;
    private final UserLessonProgressRepository progressRepository;
    private final UploadStorage uploadStorage;

    private String formatDuration(Integer seconds) {
        if (seconds == null || seconds == 0)
            return "00:00";
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;
        if (h > 0) {
            return String.format("%02d:%02d:%02d", h, m, s);
        } else {
            return String.format("%02d:%02d", m, s);
        }
    }

    private CourseResponseDTO mapToDTO(Course course) {
        CourseResponseDTO dto = new CourseResponseDTO();
        dto.setId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setPrice(course.getPrice());
        dto.setImageUrl(course.getImageUrl());
        dto.setTeacherName(course.getTeacherEmail());
        dto.setTeacherEmail(course.getTeacherEmail());
        dto.setTeacherId(course.getTeacherId());

        // 🔥 SỬA LỖI TẠI ĐÂY: Lấy số liệu thật từ DB, nếu null thì = 0
        dto.setStudentCount(course.getStudentCount() != null ? course.getStudentCount() : 0);

        dto.setStatus(course.getStatus() != null ? course.getStatus().name() : "PENDING");
        dto.setCreatedAt(course.getCreatedAt());
        return dto;
    }

    private String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty())
            return null;
        String fileName = UUID.randomUUID() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
        Path uploadPath = uploadStorage.resolve("images");
        if (!Files.exists(uploadPath))
            Files.createDirectories(uploadPath);
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, uploadPath.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        }
        return "/images/" + fileName;
    }

    private Course checkOwnership(Long courseId, String teacherEmail) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khóa học"));
        if (!teacherEmail.equals(course.getTeacherEmail())) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa");
        }
        return course;
    }

    @Override
    public CourseResponseDTO createCourse(CourseRequestDTO request, MultipartFile image, String teacherEmail,
            Long teacherId) throws IOException {
        Course course = new Course();
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setPrice(request.getPrice());
        course.setTeacherEmail(teacherEmail);
        course.setTeacherId(teacherId);
        course.setStatus(CourseStatus.PENDING);
        // Mặc định 0 học viên
        course.setStudentCount(0);

        if (image != null && !image.isEmpty()) {
            course.setImageUrl(saveFile(image));
        }
        return mapToDTO(courseRepository.save(course));
    }

    @Override
    public List<CourseResponseDTO> getAllCourses() {
        return courseRepository.findByStatus(CourseStatus.APPROVED).stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CourseResponseDTO getCourseById(Long id) {
        return mapToDTO(courseRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found")));
    }

    @Override
    public List<CourseResponseDTO> getMyCourses(String teacherEmail) {
        return courseRepository.findByTeacherEmail(teacherEmail).stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public CourseResponseDTO updateCourse(Long id, CourseRequestDTO request, MultipartFile image, String teacherEmail)
            throws IOException {
        Course course = checkOwnership(id, teacherEmail);
        course.setTitle(request.getTitle());
        course.setDescription(request.getDescription());
        course.setPrice(request.getPrice());
        if (image != null && !image.isEmpty())
            course.setImageUrl(saveFile(image));
        return mapToDTO(courseRepository.save(course));
    }

    @Override
    public void deleteCourse(Long id, String teacherEmail) {
        courseRepository.delete(checkOwnership(id, teacherEmail));
    }

    @Override
    @Transactional
    public SectionDTO createSection(Long courseId, SectionRequestDTO request, String teacherEmail) {
        Course course = checkOwnership(courseId, teacherEmail);
        Section section = new Section();
        section.setTitle(request.getTitle());
        section.setCourse(course);
        section.setOrderIndex(course.getSections().size());
        course.getSections().add(section);
        courseRepository.save(course);
        return new SectionDTO(section.getId(), section.getTitle(), new ArrayList<>());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SectionDTO> getCourseContent(Long courseId, Long userId) {
        Course course = courseRepository.findById(courseId).orElseThrow(() -> new RuntimeException("Not found"));
        Set<Long> completedVideoIds = (userId != null)
                ? progressRepository.findCompletedVideoIdsByCourse(userId, courseId)
                : new java.util.HashSet<>();

        return course.getSections().stream().map(section -> {
            SectionDTO dto = new SectionDTO();
            dto.setId(section.getId());
            dto.setTitle(section.getTitle());
            List<LessonDTO> lessons = new ArrayList<>();
            if (section.getVideos() != null) {
                lessons.addAll(section.getVideos().stream().map(v -> {
                    LessonDTO l = new LessonDTO();
                    l.setId(v.getId());
                    l.setTitle(v.getTitle());
                    l.setType("video");
                    l.setVideoUrl(v.getVideoUrl());
                    l.setDuration(formatDuration(v.getDurationInSeconds()));
                    l.setStatus(v.getStatus());
                    l.setCompleted(completedVideoIds.contains(v.getId()));
                    return l;
                }).collect(Collectors.toList()));
            }
            if (section.getExercises() != null) {
                lessons.addAll(section.getExercises().stream().map(e -> {
                    LessonDTO l = new LessonDTO();
                    l.setId(e.getId());
                    l.setTitle(e.getTitle());
                    l.setType("exercise");
                    l.setCompleted(false);
                    return l;
                }).collect(Collectors.toList()));
            }
            dto.setLessons(lessons);
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void markVideoAsCompleted(Long videoId, Long userId) {
        if (!videoRepository.existsById(videoId))
            throw new RuntimeException("Video không tồn tại");
        if (!progressRepository.existsByUserIdAndVideoId(userId, videoId)) {
            UserLessonProgress progress = new UserLessonProgress();
            progress.setUserId(userId);
            progress.setVideoId(videoId);
            progress.setCompleted(true);
            progressRepository.save(progress);
        }
    }

    @Override
    public void approveCourse(Long courseId) {
        Course c = courseRepository.findById(courseId).orElseThrow();
        c.setStatus(CourseStatus.APPROVED);
        courseRepository.save(c);
    }

    @Override
    public void rejectCourse(Long courseId) {
        Course c = courseRepository.findById(courseId).orElseThrow();
        c.setStatus(CourseStatus.REJECTED);
        courseRepository.save(c);
    }

    @Override
    public List<CourseResponseDTO> getAllCoursesForAdmin() {
        return courseRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public void deleteCourseByAdmin(Long id) {
        courseRepository.deleteById(id);
    }

    // 🔥 IMPLEMENT HÀM TĂNG HỌC VIÊN
    @Override
    @Transactional
    public void increaseStudentCount(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new RuntimeException("Khóa học không tồn tại"));

        // Tăng thêm 1
        int currentCount = (course.getStudentCount() == null) ? 0 : course.getStudentCount();
        course.setStudentCount(currentCount + 1);

        courseRepository.save(course);
    }
}
