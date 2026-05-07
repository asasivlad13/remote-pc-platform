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
import org.springframework.web.socket.BinaryMessage;
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

    @Autowired
    private WebSocketClientHandler webSocketClientHandler;

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
        if ("FILE_PROGRESS".equals(type)) {
            Long pcId = getPcIdBySession(session);

            if (pcId != null) {
                webSocketClientHandler.broadcastFileProgress(pcId, json);
            }

            return;
        }
        if ("register".equals(type)) {
            handleRegister(session, json);
        } else if ("heartbeat".equals(type)) {
            handleHeartbeat(session, json);
        } else if ("frame".equals(type)) {
            handleFrame(session, json);
        }
    }

    private Long getPcIdBySession(WebSocketSession session) {
        for (Map.Entry<Long, WebSocketSession> entry : agentSessionsByPcId.entrySet()) {
            if (entry.getValue().getId().equals(session.getId())) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        byte[] imageData = new byte[message.getPayload().remaining()];
        message.getPayload().get(imageData);

        String mac = getMacBySession(session);
        if (mac != null) {
            Pc pc = pcRepository.findByMacAddress(mac);
            if (pc != null) {
                webSocketClientHandler.broadcastBinaryFrame(pc.getId(), imageData);
                System.out.println("📸 Binary frame (" + imageData.length + " bytes) from " + mac);
            }
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
            System.out.println("Creating new PC record for MAC: " + mac);
        } else {
            if (!pc.getName().equals(pcName)) {
                pc.setName(pcName);
                System.out.println("Updating PC name to '" + pcName + "'");
            }
            if (pc.getUser() == null || !pc.getUser().getId().equals(user.getId())) {
                pc.setUser(user);
                System.out.println("Re-assigning PC to user: " + username);
            }
        }

        if (json.has("screenWidth") && json.has("screenHeight")) {
            pc.setScreenWidth(json.get("screenWidth").asInt());
            pc.setScreenHeight(json.get("screenHeight").asInt());
            System.out.println("Screen size saved: " + pc.getScreenWidth() + "x" + pc.getScreenHeight());
        }

        if (json.has("scaleX") && json.has("scaleY")) {
            double scaleX = json.get("scaleX").asDouble();
            double scaleY = json.get("scaleY").asDouble();
            System.out.println("Scale factors: " + scaleX + " x " + scaleY);
        }

        if (json.has("webrtcUrl")) {
            pc.setWebrtcUrl(json.get("webrtcUrl").asText());
            System.out.println("WebRTC URL saved: " + pc.getWebrtcUrl());
        }

        if (json.has("streamName")) {
            pc.setStreamName(json.get("streamName").asText());
            System.out.println("Stream name saved: " + pc.getStreamName());
        }

        pc.setStatus(PcStatus.ONLINE);
        pc.setLastConnection(LocalDateTime.now());
        pcRepository.save(pc);

        agentSessionsByPcId.put(pc.getId(), session);
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

                if (pc.getStatus() != PcStatus.SLEEP) {
                    pc.setStatus(PcStatus.ONLINE);
                }

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

    public void sendNotificationToAgent(Long pcId, String message) throws Exception {
        WebSocketSession agentSession = agentSessionsByPcId.get(pcId);

        if (agentSession != null && agentSession.isOpen()) {
            Map<String, String> notification = Map.of(
                    "type", "notification",
                    "message", message
            );

            String json = objectMapper.writeValueAsString(notification);

            agentSession.sendMessage(new TextMessage(json));
            System.out.println("📢 Notification sent to agent for PC " + pcId + ": " + json);
        } else {
            System.out.println("Agent not connected for PC " + pcId + ", cannot send notification");
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
                System.out.println("PC " + pc.getName() + " (" + mac + ") set to OFFLINE");
            }
            agentSessions.remove(mac);
        }

        agentSessionsByPcId.values().remove(session);

        System.out.println("Agent disconnected: " + session.getId());
    }
}