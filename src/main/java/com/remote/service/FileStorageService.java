package com.remote.service;

import com.remote.dto.StoredFileInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileStorageService {

    private final Path storageDir;
    private final Map<String, StoredFileInfo> files = new ConcurrentHashMap<>();

    public FileStorageService(
            @Value("${remote.files.storage-dir:uploads/remote-files}") String storageDir
    ) throws Exception {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(this.storageDir);
    }

    public StoredFileInfo store(MultipartFile file, String publicBaseUrl) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        String fileId = UUID.randomUUID().toString();
        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String storedFileName = fileId + "_" + originalFileName;

        Path targetPath = storageDir.resolve(storedFileName).normalize();

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        String downloadUrl = publicBaseUrl + "/api/files/download/" + fileId;

        StoredFileInfo info = new StoredFileInfo(
                fileId,
                originalFileName,
                file.getSize(),
                downloadUrl
        );

        files.put(fileId, info);

        return info;
    }

    public Resource loadAsResource(String fileId) throws Exception {
        StoredFileInfo info = files.get(fileId);

        if (info == null) {
            throw new IllegalArgumentException("File not found by id: " + fileId);
        }

        Path filePath = storageDir.resolve(fileId + "_" + info.getFileName()).normalize();

        if (!Files.exists(filePath)) {
            throw new IllegalArgumentException("File does not exist on disk");
        }

        try {
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Cannot load file", e);
        }
    }

    public StoredFileInfo getInfo(String fileId) {
        return files.get(fileId);
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown_file";
        }

        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}