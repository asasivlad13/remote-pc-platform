package com.remote.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remote.model.Pc;
import com.remote.repository.PcRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketClientHandler extends TextWebSocketHandler {

    @Autowired
    private PcRepository pcRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Храним ВСЕ сессии веб-клиентов (sessionId -> WebSocketSession)
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Храним, какой ПК смотрит каждый клиент (sessionId -> pcId)
    private final Map<String, Long> clientWatching = new ConcurrentHashMap<>();

    // Храним последние кадры для каждого ПК (pcId -> base64 image)
    private final Map<Long, String> lastFrames = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("Web client connected: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received from client: " + payload);

        JsonNode json = objectMapper.readTree(payload);
        String type = json.get("type").asText();

        if ("watch".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            clientWatching.put(session.getId(), pcId);
            System.out.println("Client " + session.getId() + " is watching PC " + pcId);

            // Отправляем последний кадр, если есть
            String lastFrame = lastFrames.get(pcId);
            if (lastFrame != null) {
                session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + lastFrame + "\"}"));
                System.out.println("Sent last frame to client " + session.getId());
            }
        } else if ("stop".equals(type)) {
            clientWatching.remove(session.getId());
            System.out.println("Client " + session.getId() + " stopped watching");
        }
    }

    public void broadcastFrame(Long pcId, String base64Image) {
        // Сохраняем последний кадр
        lastFrames.put(pcId, base64Image);

        System.out.println("Broadcasting frame to clients watching PC " + pcId);

        // Отправляем всем клиентам, которые смотрят этот ПК
        for (Map.Entry<String, Long> entry : clientWatching.entrySet()) {
            if (entry.getValue().equals(pcId)) {
                WebSocketSession session = sessions.get(entry.getKey());
                if (session != null && session.isOpen()) {
                    try {
                        session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + base64Image + "\"}"));
                        System.out.println("Frame sent to client: " + entry.getKey());
                    } catch (Exception e) {
                        System.err.println("Error sending frame to client: " + e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        clientWatching.remove(session.getId());
        System.out.println("Web client disconnected: " + session.getId());
    }
}