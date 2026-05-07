package com.remote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.dto.StoredFileInfo;
import com.remote.handler.AgentWebSocketHandler;
import com.remote.service.FileStorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileTransferController {

    private final FileStorageService fileStorageService;
    private final AgentWebSocketHandler agentWebSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${remote.server.public-url:}")
    private String publicUrl;

    public FileTransferController(FileStorageService fileStorageService,
                                  AgentWebSocketHandler agentWebSocketHandler) {
        this.fileStorageService = fileStorageService;
        this.agentWebSocketHandler = agentWebSocketHandler;
    }

    @PostMapping("/upload")
    public StoredFileInfo uploadFile(@RequestParam("pcId") Long pcId,
                                     @RequestParam("file") MultipartFile file,
                                     HttpServletRequest request) throws Exception {
        String baseUrl = resolveBaseUrl(request);

        StoredFileInfo storedFileInfo = fileStorageService.store(file, baseUrl);

        Map<String, Object> command = new HashMap<>();
        command.put("type", "command");
        command.put("pcId", pcId);
        command.put("action", "FILE_DOWNLOAD");
        command.put("fileId", storedFileInfo.getFileId());
        command.put("fileName", storedFileInfo.getFileName());
        command.put("fileSize", storedFileInfo.getFileSize());
        command.put("downloadUrl", storedFileInfo.getDownloadUrl());

        agentWebSocketHandler.sendCommandToAgent(
                pcId,
                objectMapper.valueToTree(command)
        );

        System.out.println("📤 File uploaded to server and download command sent:");
        System.out.println("  PC ID: " + pcId);
        System.out.println("  File: " + storedFileInfo.getFileName());
        System.out.println("  URL: " + storedFileInfo.getDownloadUrl());

        return storedFileInfo;
    }

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileId) throws Exception {
        StoredFileInfo info = fileStorageService.getInfo(fileId);

        if (info == null) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = fileStorageService.loadAsResource(fileId);

        String encodedFileName = URLEncoder.encode(info.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName
                )
                .body(resource);
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        if (publicUrl != null && !publicUrl.isBlank()) {
            return publicUrl;
        }

        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
    }
}