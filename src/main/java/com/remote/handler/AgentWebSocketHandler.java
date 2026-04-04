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

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Received: " + payload);

        JsonNode json = objectMapper.readTree(payload);
        String type = json.get("type").asText();

        if ("register".equals(type)) {
            String token = json.get("token").asText();
            String pcName = json.get("pcName").asText();
            String mac = json.get("mac").asText();

            // 1. Проверяем токен
            if (!jwtUtil.validateToken(token)) {
                System.out.println("Invalid token");
                session.sendMessage(new TextMessage("{\"error\":\"Invalid token\"}"));
                session.close();
                return;
            }

            // 2. Получаем пользователя из токена
            String username = jwtUtil.extractUsername(token);
            User user = userRepository.findByUsername(username).orElse(null);

            if (user == null) {
                System.out.println("User not found: " + username);
                session.sendMessage(new TextMessage("{\"error\":\"User not found\"}"));
                session.close();
                return;
            }

            System.out.println("User found: " + username + ", id=" + user.getId());

            // 3. Ищем или создаём ПК
            Pc pc = pcRepository.findByMacAddress(mac);
            if (pc == null) {
                pc = new Pc();
                pc.setName(pcName);
                pc.setMacAddress(mac);
                System.out.println("Creating new PC");
            } else {
                System.out.println("PC already exists, updating");
            }

            // 4. Обновляем поля
            pc.setName(pcName);
            pc.setStatus(PcStatus.ONLINE);
            pc.setLastConnection(LocalDateTime.now());
            pc.setUser(user);  // Привязываем пользователя

            // 5. Сохраняем
            Pc savedPc = pcRepository.save(pc);
            System.out.println("Saved PC with id=" + savedPc.getId() + ", user_id=" + (savedPc.getUser() != null ? savedPc.getUser().getId() : "NULL"));

            // 6. Сохраняем сессию
            agentSessions.put(mac, session);
            session.sendMessage(new TextMessage("{\"status\":\"registered\"}"));

            System.out.println("Agent registered: " + pcName + " (" + mac + ") for user: " + username);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String macToRemove = null;
        for (Map.Entry<String, WebSocketSession> entry : agentSessions.entrySet()) {
            if (entry.getValue().equals(session)) {
                macToRemove = entry.getKey();
                break;
            }
        }
        if (macToRemove != null) {
            agentSessions.remove(macToRemove);
            Pc pc = pcRepository.findByMacAddress(macToRemove);
            if (pc != null) {
                pc.setStatus(PcStatus.OFFLINE);
                pcRepository.save(pc);
                System.out.println("PC set to OFFLINE: " + macToRemove);
            }
        }
        System.out.println("Agent disconnected: " + session.getId());
    }
}