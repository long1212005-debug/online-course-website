package com.soa.course_service.service.impl;

import com.soa.course_service.config.UploadStorage;
import com.soa.course_service.entity.Banner;
import com.soa.course_service.repository.BannerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl {

    private final BannerRepository bannerRepository;
    private final UploadStorage uploadStorage;

    public List<Banner> getAllBanners() {
        return bannerRepository.findAll();
    }

    public Banner getActiveBanner() {
        return bannerRepository.findByIsActiveTrue().orElse(null);
    }

    @Transactional
    public Banner createBanner(String title, String subtitle, String btnText, String btnLink, MultipartFile image)
            throws IOException {
        Banner banner = new Banner();
        banner.setTitle(title);
        banner.setSubtitle(subtitle);
        banner.setButtonText(btnText);
        banner.setButtonLink(btnLink);
        banner.setIsActive(false); // Mặc định tạo xong chưa kích hoạt ngay

        if (image != null && !image.isEmpty()) {
            String fileName = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path path = uploadStorage.resolve("images").resolve(fileName);
            Files.createDirectories(path.getParent());
            Files.write(path, image.getBytes());
            banner.setImageUrl("/images/" + fileName);
        }

        return bannerRepository.save(banner);
    }

    @Transactional
    public void activateBanner(Long id) {
        // 1. Disable tất cả banner khác
        bannerRepository.disableAllBanners();

        // 2. Enable banner được chọn
        Banner banner = bannerRepository.findById(id).orElseThrow(() -> new RuntimeException("Banner not found"));
        banner.setIsActive(true);
        bannerRepository.save(banner);
    }

    public void deleteBanner(Long id) {
        bannerRepository.deleteById(id);
    }
}
