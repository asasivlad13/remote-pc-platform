package com.remote.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "connection_logs")
public class ConnectionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String pcName;
    private String action;
    private LocalDateTime timestamp;
    private String clientIp;

    @ManyToOne
    @JoinColumn(name = "pc_id")
    private Pc pc;

    // Конструкторы
    public ConnectionLog() {}

    public ConnectionLog(String username, String pcName, String action, String clientIp) {
        this.username = username;
        this.pcName = pcName;
        this.action = action;
        this.clientIp = clientIp;
        this.timestamp = LocalDateTime.now();
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPcName() { return pcName; }
    public void setPcName(String pcName) { this.pcName = pcName; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }

    public Pc getPc() { return pc; }
    public void setPc(Pc pc) { this.pc = pc; }
}