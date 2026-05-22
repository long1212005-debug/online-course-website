package com.soa.enrollment_service.repository;

import com.soa.enrollment_service.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByStudentEmailIgnoreCaseAndCourseId(String studentEmail, Long courseId);

    List<Enrollment> findByStudentEmailIgnoreCase(String studentEmail);
}
