package com.campus.competition.controller;

import com.campus.competition.common.BusinessException;
import com.campus.competition.common.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传接口（正式版：本地存储；可平滑替换为 OSS/MinIO）
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private static final String[] ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp",
            ".pdf", ".doc", ".docx", ".txt", ".md", ".ppt", ".pptx", ".xls", ".xlsx",
            ".zip", ".rar", ".7z", ".tar", ".gz"};

    private final Path uploadDir = Paths.get("uploads").toAbsolutePath();

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
        }
        boolean allowed = false;
        for (String e : ALLOWED_EXT) {
            if (e.equals(ext)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            throw new BusinessException("不支持的文件类型: " + ext);
        }

        Files.createDirectories(uploadDir);
        String filename = UUID.randomUUID().toString().replace("-", "") + ext;
        file.transferTo(uploadDir.resolve(filename).toFile());

        Map<String, Object> data = new HashMap<>();
        data.put("url", "/uploads/" + filename);
        data.put("name", originalName);
        data.put("size", file.getSize());
        return Result.success(data);
    }
}
