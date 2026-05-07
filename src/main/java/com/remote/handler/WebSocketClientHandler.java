package com.remote.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.config.JwtUtil;
import com.remote.model.ConnectionLog;
import com.remote.model.Pc;
import com.remote.repository.ConnectionLogRepository;
import com.remote.repository.PcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketClientHandler extends TextWebSocketHandler {

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private ConnectionLogRepository connectionLogRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    @Lazy
    private WebSocketClientHandler webSocketClientHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> clientWatching = new ConcurrentHashMap<>();
    private final Map<Long, String> lastFrames = new ConcurrentHashMap<>();

    private final Map<String, Long> sessionLogIds = new ConcurrentHashMap<>();
    private final Map<String, Double> fpsSum = new ConcurrentHashMap<>();
    private final Map<String, Double> latencySum = new ConcurrentHashMap<>();
    private final Map<String, Integer> metricsCount = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        System.out.println("Web client connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received from client: " + payload);

        JsonNode json = objectMapper.readTree(payload);

        if (!json.has("type")) {
            return;
        }

        String type = json.get("type").asText();

        if ("ping".equals(type)) {
            session.sendMessage(new TextMessage(payload.replace("\"ping\"", "\"pong\"")));
            return;
        }

        if ("watch".equals(type)) {
            handleWatch(session, json);
            return;
        }

        if ("metrics".equals(type)) {
            handleMetrics(session, json);
            return;
        }

        if ("stop".equals(type)) {
            closeSessionLog(session);
            clientWatching.remove(session.getId());
            return;
        }

        if ("command".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            agentWebSocketHandler.sendCommandToAgent(pcId, json);
            return;
        }

        if ("settings".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            agentWebSocketHandler.sendCommandToAgent(pcId, json);
            session.sendMessage(new TextMessage("{\"type\":\"settings_applied\"}"));
        }
    }

    private void handleWatch(WebSocketSession session, JsonNode json) throws Exception {
        Long pcId = json.get("pcId").asLong();
        clientWatching.put(session.getId(), pcId);

        String username = extractUsernameFromJson(json);
        String clientIp = extractClientIp(session);
        String clientInfo = extractClientInfo(json);
        String mode = json.has("mode") ? json.get("mode").asText() : "Control";

        Pc pc = pcRepository.findById(pcId).orElse(null);

        if (pc != null) {
            ConnectionLog log = new ConnectionLog(username, pc.getName(), "CONNECT", clientIp);
            log.setPc(pc);
            log.setClientInfo(clientInfo);
            log.setMode(mode);
            log.setAvgFps(0.0);
            log.setAvgLatency(0.0);
            log.setFilesSent(0);
            log.setIssues("");

            ConnectionLog saved = connectionLogRepository.save(log);
            sessionLogIds.put(session.getId(), saved.getId());

            fpsSum.put(session.getId(), 0.0);
            latencySum.put(session.getId(), 0.0);
            metricsCount.put(session.getId(), 0);

            String notificationMessage =
                    "К вашему ПК \"" + pc.getName() + "\" подключился: "
                            + username
                            + " | IP: " + clientIp
                            + " | Устройство: " + clientInfo;

            agentWebSocketHandler.sendNotificationToAgent(pcId, notificationMessage);

            System.out.println("📝 Logged connection: " + username + " -> " + pc.getName());
            System.out.println("🔔 Notification sent: " + notificationMessage);
        }

        String lastFrame = lastFrames.get(pcId);
        if (lastFrame != null) {
            session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + lastFrame + "\"}"));
        }
    }

    private void handleMetrics(WebSocketSession session, JsonNode json) {
        Long logId = sessionLogIds.get(session.getId());

        if (logId == null) {
            return;
        }

        double fps = json.has("fps") ? json.get("fps").asDouble(0.0) : 0.0;
        double latency = json.has("latency") ? json.get("latency").asDouble(0.0) : 0.0;
        String mode = json.has("mode") ? json.get("mode").asText("Control") : "Control";

        fpsSum.merge(session.getId(), fps, Double::sum);
        latencySum.merge(session.getId(), latency, Double::sum);
        metricsCount.merge(session.getId(), 1, Integer::sum);

        int count = metricsCount.getOrDefault(session.getId(), 0);

        if (count <= 0) {
            return;
        }

        double avgFps = fpsSum.getOrDefault(session.getId(), 0.0) / count;
        double avgLatency = latencySum.getOrDefault(session.getId(), 0.0) / count;

        connectionLogRepository.findById(logId).ifPresent(log -> {
            log.setAvgFps(round(avgFps));
            log.setAvgLatency(round(avgLatency));
            log.setMode(mode);
            connectionLogRepository.save(log);
        });
    }

    private void closeSessionLog(WebSocketSession session) {
        Long logId = sessionLogIds.remove(session.getId());

        clientWatching.remove(session.getId());
        fpsSum.remove(session.getId());
        latencySum.remove(session.getId());
        metricsCount.remove(session.getId());

        if (logId == null) {
            return;
        }

        connectionLogRepository.findById(logId).ifPresent(log -> {
            LocalDateTime disconnectedAt = LocalDateTime.now();
            log.setDisconnectedAt(disconnectedAt);

            if (log.getTimestamp() != null) {
                long seconds = Duration.between(log.getTimestamp(), disconnectedAt).getSeconds();
                log.setDurationSeconds((int) Math.max(seconds, 0));
            }

            connectionLogRepository.save(log);

            System.out.println("📝 Session closed: logId=" + logId + ", duration=" + log.getDurationSeconds() + " sec");
        });
    }

    private String extractUsernameFromJson(JsonNode json) {
        try {
            if (json.has("token")) {
                String token = json.get("token").asText();

                if (token != null && jwtUtil.validateToken(token)) {
                    return jwtUtil.extractUsername(token);
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot extract username from token: " + e.getMessage());
        }

        return "unknown";
    }

    private String extractClientIp(WebSocketSession session) {
        InetSocketAddress address = session.getRemoteAddress();

        if (address == null || address.getAddress() == null) {
            return "unknown";
        }

        return address.getAddress().getHostAddress();
    }

    private String extractClientInfo(JsonNode json) {
        String platform = json.has("platform") ? json.get("platform").asText() : "unknown platform";
        String browser = json.has("browser") ? json.get("browser").asText() : "unknown browser";

        return platform + ", " + browser;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        lastFrames.put(pcId, base64Image);

        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + base64Image + "\"}"));
                    } catch (Exception e) {
                        System.err.println("Error sending frame: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastBinaryFrame(Long pcId, byte[] imageData) {
        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new BinaryMessage(imageData));
                    } catch (Exception e) {
                        System.err.println("Error sending binary frame: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastFileProgress(Long pcId, JsonNode progressJson) {
        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());

                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage(progressJson.toString()));
                    } catch (Exception e) {
                        System.err.println("Error sending file progress: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSessionLog(session);
        sessions.remove(session.getId());
        System.out.println("Web client disconnected: " + session.getId());
    }
}