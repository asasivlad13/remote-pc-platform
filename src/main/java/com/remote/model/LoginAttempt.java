package com.remote.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "login_attempts")
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ipAddress;
    private int attempts;
    private LocalDateTime blockUntil;
    private LocalDateTime lastAttempt;

    // Конструкторы, геттеры, сеттеры
    public LoginAttempt() {}

    public LoginAttempt(String ipAddress) {
        this.ipAddress = ipAddress;
        this.attempts = 0;
        this.lastAttempt = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public LocalDateTime getBlockUntil() { return blockUntil; }
    public void setBlockUntil(LocalDateTime blockUntil) { this.blockUntil = blockUntil; }
    public LocalDateTime getLastAttempt() { return lastAttempt; }
    public void setLastAttempt(LocalDateTime lastAttempt) { this.lastAttempt = lastAttempt; }
}