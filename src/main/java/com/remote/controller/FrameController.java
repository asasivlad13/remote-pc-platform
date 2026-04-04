package com.remote.controller;

import com.remote.handler.WebSocketClientHandler;
import com.remote.model.Pc;
import com.remote.repository.PcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/frames")
public class FrameController {

    @Autowired
    private PcRepository pcRepository;
    @Autowired
    private WebSocketClientHandler webSocketClientHandler;
    // Хранилище последних кадров (mac -> base64 image)
    private final Map<String, String> lastFrames = new ConcurrentHashMap<>();

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFrame(@RequestBody Map<String, String> payload) {
        String mac = payload.get("mac");
        String imageBase64 = payload.get("image");

        if (mac == null || imageBase64 == null) {
            return ResponseEntity.badRequest().body("Missing mac or image");
        }

        // Находим PC по mac
        Pc pc = pcRepository.findByMacAddress(mac);
        if (pc == null) {
            return ResponseEntity.badRequest().body("PC not found");
        }

        lastFrames.put(mac, imageBase64);
        System.out.println("📸 Frame saved for " + mac + ", size: " + imageBase64.length() + " chars");

        // Ретранслируем всем веб-клиентам, которые смотрят этот ПК
        webSocketClientHandler.broadcastFrame(pc.getId(), imageBase64);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/latest/{mac}")
    public ResponseEntity<?> getLatestFrame(@PathVariable String mac) {
        String imageBase64 = lastFrames.get(mac);
        if (imageBase64 == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("image", imageBase64));
    }

    @GetMapping("/my-pcs-frames")
    public ResponseEntity<?> getMyPcsFrames() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        var pcs = pcRepository.findByUser(pcRepository.findByUser(null).get(0).getUser()); // Упрощённо

        Map<String, String> result = new ConcurrentHashMap<>();
        for (Pc pc : pcs) {
            String frame = lastFrames.get(pc.getMacAddress());
            if (frame != null) {
                result.put(pc.getMacAddress(), frame);
            }
        }
        return ResponseEntity.ok(result);
    }
}