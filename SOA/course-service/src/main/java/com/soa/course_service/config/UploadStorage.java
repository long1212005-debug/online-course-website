package com.soa.course_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class UploadStorage {

    private final Path root;

    public UploadStorage(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public Path resolve(String subDir) {
        return root.resolve(subDir).normalize();
    }

    public String resourceLocation(String subDir) {
        return "file:" + resolve(subDir).toString().replace("\\", "/") + "/";
    }

    public Path root() {
        return root;
    }
}
