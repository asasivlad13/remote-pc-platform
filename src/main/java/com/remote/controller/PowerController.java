package com.remote.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remote.config.JwtUtil;
import com.remote.handler.AgentWebSocketHandler;
import com.remote.model.Pc;
import com.remote.model.PcStatus;
import com.remote.model.User;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/pcs")
public class PowerController {

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Autowired
    private JwtUtil jwtUtil;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/{id}/sleep")
    public ResponseEntity<?> softSleepPc(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        Pc pc = getUserPc(id, authHeader);

        if (pc == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён"));
        }

        try {
            ObjectNode command = objectMapper.createObjectNode();
            command.put("type", "command");
            command.put("pcId", pc.getId());
            command.put("action", "SOFT_SLEEP");

            agentWebSocketHandler.sendCommandToAgent(pc.getId(), command);

            pc.setStatus(PcStatus.SLEEP);
            pcRepository.save(pc);

            return ResponseEntity.ok(Map.of("message", "Режим ожидания включён"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Не удалось включить режим ожидания"));
        }
    }

    @PostMapping("/{id}/wake")
    public ResponseEntity<?> softWakePc(
            @PathVariable Long id,
            @RequestHeader("Authorization") String authHeader
    ) {
        Pc pc = getUserPc(id, authHeader);

        if (pc == null) {
            return ResponseEntity.status(403).body(Map.of("error", "Доступ запрещён"));
        }

        try {
            ObjectNode command = objectMapper.createObjectNode();
            command.put("type", "command");
            command.put("pcId", pc.getId());
            command.put("action", "SOFT_WAKE");

            agentWebSocketHandler.sendCommandToAgent(pc.getId(), command);

            pc.setStatus(PcStatus.ONLINE);
            pcRepository.save(pc);

            return ResponseEntity.ok(Map.of("message", "ПК выведен из режима ожидания"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Не удалось вывести ПК из режима ожидания"));
        }
    }

    private Pc getUserPc(Long pcId, String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        String token = authHeader.substring(7);

        if (!jwtUtil.validateToken(token)) {
            return null;
        }

        String username = jwtUtil.extractUsername(token);

        User user = userRepository.findByUsername(username).orElse(null);
        Pc pc = pcRepository.findById(pcId).orElse(null);

        if (user == null || pc == null || pc.getUser() == null) {
            return null;
        }

        if (!pc.getUser().getId().equals(user.getId())) {
            return null;
        }

        return pc;
    }
}