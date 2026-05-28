package com.soa.course_service.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

        private final UploadStorage uploadStorage;

        public WebConfig(UploadStorage uploadStorage) {
                this.uploadStorage = uploadStorage;
        }

        @Override
        public void addResourceHandlers(ResourceHandlerRegistry registry) {

                // 📌 Lấy đường dẫn gốc project course-service
                String projectRoot = uploadStorage.root().toString();

                String uploadPath = "file:" + projectRoot.replace("\\", "/") + "/";

                System.out.println(">>> UPLOAD PATH = " + uploadPath);

                // HLS
                registry.addResourceHandler("/hls/**")
                                .addResourceLocations(uploadPath + "hls/")
                                .setCachePeriod(0);

                // IMAGES
                registry.addResourceHandler("/images/**")
                                .addResourceLocations(uploadPath + "images/")
                                .setCachePeriod(3600);

                // EXERCISES
                registry.addResourceHandler("/exercises/**")
                                .addResourceLocations(uploadPath + "exercises/")
                                .setCachePeriod(3600);
        }
}
