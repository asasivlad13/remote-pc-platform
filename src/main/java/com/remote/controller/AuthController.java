package com.remote.controller;

import com.remote.config.JwtUtil;
import com.remote.model.ConnectionLog;
import com.remote.model.LoginAttempt;
import com.remote.model.User;
import com.remote.repository.ConnectionLogRepository;
import com.remote.repository.LoginAttemptRepository;
import com.remote.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private LoginAttemptRepository loginAttemptRepository;

    @Autowired
    private ConnectionLogRepository connectionLogRepository;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    // Получить IP клиента
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) ip = request.getRemoteAddr();
        return ip;
    }

    // Проверка блокировки
    private boolean isBlocked(String ipAddress) {
        var attempt = loginAttemptRepository.findByIpAddress(ipAddress);
        if (attempt.isPresent() && attempt.get().getBlockUntil() != null) {
            if (LocalDateTime.now().isBefore(attempt.get().getBlockUntil())) {
                return true;
            }
        }
        return false;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user, HttpServletRequest request) {
        String ip = getClientIp(request);

        if (isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Too many attempts. Try again later."));
        }

        // Валидация пароля
        String password = user.getPassword();
        if (password.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 6 characters"));
        }

        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username already exists"));
        }

        user.setPassword(encoder.encode(password));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody User loginRequest, HttpServletRequest request) {
        String ip = getClientIp(request);

        // Проверка блокировки
        if (isBlocked(ip)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Too many failed attempts. Try again in 15 minutes."));
        }

        var userOpt = userRepository.findByUsername(loginRequest.getUsername());

        if (userOpt.isEmpty() || !encoder.matches(loginRequest.getPassword(), userOpt.get().getPassword())) {
            // Логируем неудачную попытку
            LoginAttempt attempt = loginAttemptRepository.findByIpAddress(ip).orElse(new LoginAttempt(ip));
            attempt.setAttempts(attempt.getAttempts() + 1);
            attempt.setLastAttempt(LocalDateTime.now());

            if (attempt.getAttempts() >= 5) {
                attempt.setBlockUntil(LocalDateTime.now().plusMinutes(15));
                attempt.setAttempts(0);
            }
            loginAttemptRepository.save(attempt);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        // Сброс попыток при успешном входе
        loginAttemptRepository.findByIpAddress(ip).ifPresent(attempt -> {
            loginAttemptRepository.delete(attempt);
        });

        String token = jwtUtil.generateToken(userOpt.get().getUsername());
        return ResponseEntity.ok(Map.of("token", token));
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid token"));
        }

        String oldPassword = payload.get("oldPassword");
        String newPassword = payload.get("newPassword");

        User user = userRepository.findByUsername(username).orElseThrow();

        if (!encoder.matches(oldPassword, user.getPassword())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Old password is incorrect"));
        }

        if (newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "New password must be at least 6 characters"));
        }

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getLogs(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        String username = jwtUtil.extractUsername(token);

        if (!jwtUtil.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<ConnectionLog> logs = connectionLogRepository.findByUsernameOrderByTimestampDesc(username);
        return ResponseEntity.ok(logs);
    }
}