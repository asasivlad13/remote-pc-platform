package com.remote.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private AgentWebSocketHandler agentWebSocketHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Long> clientWatching = new ConcurrentHashMap<>();
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

            String lastFrame = lastFrames.get(pcId);
            if (lastFrame != null) {
                session.sendMessage(new TextMessage("{\"type\":\"frame\",\"image\":\"" + lastFrame + "\"}"));
            }
        } else if ("stop".equals(type)) {
            clientWatching.remove(session.getId());
            System.out.println("Client " + session.getId() + " stopped watching");
        } else if ("command".equals(type)) {
            Long pcId = json.get("pcId").asLong();
            System.out.println("Command from client for PC " + pcId + ": " + json);
            agentWebSocketHandler.sendCommandToAgent(pcId, json);
        }
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

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        clientWatching.remove(session.getId());
        System.out.println("Web client disconnected: " + session.getId());
    }
}