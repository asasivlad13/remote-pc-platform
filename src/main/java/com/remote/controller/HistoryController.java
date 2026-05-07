package com.remote.controller;

import com.remote.dto.HistoryResponse;
import com.remote.model.ConnectionLog;
import com.remote.repository.ConnectionLogRepository;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final ConnectionLogRepository connectionLogRepository;

    public HistoryController(ConnectionLogRepository connectionLogRepository) {
        this.connectionLogRepository = connectionLogRepository;
    }

    @GetMapping
    public List<HistoryResponse> getHistory(Authentication authentication) {
        String username = authentication.getName();

        return connectionLogRepository.findByUsernameOrderByTimestampDesc(username)
                .stream()
                .map(log -> new HistoryResponse(
                        log.getId(),
                        log.getSessionId(),
                        log.getUsername(),
                        log.getPcName(),
                        log.getClientIp(),
                        log.getClientInfo(),
                        log.getMode(),
                        log.getTimestamp(),
                        log.getDisconnectedAt(),
                        log.getDurationSeconds(),
                        log.getAvgFps(),
                        log.getAvgLatency(),
                        log.getFilesSent(),
                        log.getIssues()
                ))
                .toList();
    }
}