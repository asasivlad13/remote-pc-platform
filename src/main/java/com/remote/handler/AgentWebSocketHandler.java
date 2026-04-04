package com.remote.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.config.JwtUtil;
import com.remote.model.Pc;
import com.remote.model.PcStatus;
import com.remote.model.User;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private UserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, WebSocketSession> agentSessions = new ConcurrentHashMap<>();
    private final Map<Long, WebSocketSession> agentSessionsByPcId = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("Agent connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received text, length: " + payload.length() + " chars");

        if (payload.length() > 1000) {
            System.out.println("  (first 100 chars): " + payload.substring(0, 100) + "...");
        } else {
            System.out.println("  content: " + payload);
        }

        JsonNode json = objectMapper.readTree(payload);
        String type = json.get("type").asText();

        if ("register".equals(type)) {
            handleRegister(session, json);
        } else if ("heartbeat".equals(type)) {
            handleHeartbeat(session, json);
        } else if ("frame".equals(type)) {
            handleFrame(session, json);
        }
    }

    private void handleRegister(WebSocketSession session, JsonNode json) throws Exception {
        String token = json.get("token").asText();
        String pcName = json.get("pcName").asText();
        String mac = json.get("mac").asText();

        if (!jwtUtil.validateToken(token)) {
            session.sendMessage(new TextMessage("{\"error\":\"Invalid token\"}"));
            session.close();
            return;
        }

        String username = jwtUtil.extractUsername(token);
        User user = userRepository.findByUsername(username).orElse(null);

        if (user == null) {
            session.sendMessage(new TextMessage("{\"error\":\"User not found\"}"));
            session.close();
            return;
        }

        Pc pc = pcRepository.findByMacAddress(mac);
        if (pc == null) {
            pc = new Pc();
            pc.setName(pcName);
            pc.setMacAddress(mac);
            pc.setUser(user);
        }

        pc.setStatus(PcStatus.ONLINE);
        pc.setLastConnection(LocalDateTime.now());
        pcRepository.save(pc);

        // Сохраняем сессию по PC ID для отправки команд
        agentSessionsByPcId.put(pc.getId(), session);
        System.out.println("Agent session stored for PC ID: " + pc.getId());

        agentSessions.put(mac, session);
        session.sendMessage(new TextMessage("{\"status\":\"registered\"}"));

        System.out.println("Agent registered: " + pcName + " (" + mac + ") for user: " + username);
    }

    private void handleHeartbeat(WebSocketSession session, JsonNode json) {
        String mac = getMacBySession(session);
        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);
            if (pc != null) {
                pc.setLastConnection(LocalDateTime.now());
                pcRepository.save(pc);
                System.out.println("Heartbeat from: " + mac);
            }
        }
    }

    private void handleFrame(WebSocketSession session, JsonNode json) {
        String mac = getMacBySession(session);
        if (mac != null) {
            String imageBase64 = json.get("image").asText();
            System.out.println("📸 Frame from " + mac + ", size: " + imageBase64.length() + " chars");
        }
    }

    private String getMacBySession(WebSocketSession session) {
        for (Map.Entry<String, WebSocketSession> entry : agentSessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public void sendCommandToAgent(Long pcId, JsonNode command) throws Exception {
        WebSocketSession agentSession = agentSessionsByPcId.get(pcId);
        if (agentSession != null && agentSession.isOpen()) {
            String commandJson = objectMapper.writeValueAsString(command);
            agentSession.sendMessage(new TextMessage(commandJson));
            System.out.println("Command forwarded to agent for PC " + pcId + ": " + commandJson);
        } else {
            System.out.println("Agent not connected for PC " + pcId);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String mac = getMacBySession(session);
        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);
            if (pc != null) {
                pc.setStatus(PcStatus.OFFLINE);
                pcRepository.save(pc);
            }
            agentSessions.remove(mac);
        }

        // Удаляем из agentSessionsByPcId
        agentSessionsByPcId.values().remove(session);

        System.out.println("Agent disconnected: " + session.getId());
    }
}