package com.remote.controller;

import com.remote.dto.PcResponseDto;
import com.remote.model.Pc;
import com.remote.model.User;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pcs")
public class PcController {

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<PcResponseDto> getMyPcs() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Pc> pcs = pcRepository.findByUser(user);

        return pcs.stream()
                .map(pc -> new PcResponseDto(
                        pc.getId(),
                        pc.getName(),
                        pc.getMacAddress(),
                        pc.getStatus(),
                        pc.getLastConnection()
                ))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPcById(@PathVariable Long id) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        Pc pc = pcRepository.findById(id).orElse(null);
        if (pc == null || !pc.getUser().getId().equals(user.getId())) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", pc.getId());
        response.put("name", pc.getName());
        response.put("macAddress", pc.getMacAddress());
        response.put("status", pc.getStatus());
        response.put("lastConnection", pc.getLastConnection());
        response.put("screenWidth", pc.getScreenWidth() != null ? pc.getScreenWidth() : 1920);
        response.put("screenHeight", pc.getScreenHeight() != null ? pc.getScreenHeight() : 1080);
        response.put("webrtcUrl", pc.getWebrtcUrl());
        response.put("streamName", pc.getStreamName());

        return ResponseEntity.ok(response);
    }
}