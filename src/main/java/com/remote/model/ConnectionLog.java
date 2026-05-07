package com.remote.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "connection_logs")
public class ConnectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID sessionId;

    private String username;
    private String pcName;
    private String action;

    private LocalDateTime timestamp;
    private LocalDateTime disconnectedAt;

    private Integer durationSeconds;

    private String clientIp;

    @Column(length = 1000)
    private String clientInfo;

    private String mode;

    private Double avgFps;
    private Double avgLatency;

    private Integer filesSent;

    @Column(length = 2000)
    private String issues;

    @ManyToOne
    @JoinColumn(name = "pc_id")
    private Pc pc;

    public ConnectionLog() {
    }

    public ConnectionLog(String username, String pcName, String action, String clientIp) {
        this.sessionId = UUID.randomUUID();
        this.username = username;
        this.pcName = pcName;
        this.action = action;
        this.clientIp = clientIp;
        this.timestamp = LocalDateTime.now();
        this.mode = "Control";
        this.avgFps = 0.0;
        this.avgLatency = 0.0;
        this.filesSent = 0;
        this.issues = "";
    }

    public Long getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public String getPcName() {
        return pcName;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public void setDisconnectedAt(LocalDateTime disconnectedAt) {
        this.disconnectedAt = disconnectedAt;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Integer durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Double getAvgFps() {
        return avgFps;
    }

    public void setAvgFps(Double avgFps) {
        this.avgFps = avgFps;
    }

    public Double getAvgLatency() {
        return avgLatency;
    }

    public void setAvgLatency(Double avgLatency) {
        this.avgLatency = avgLatency;
    }

    public Integer getFilesSent() {
        return filesSent;
    }

    public void setFilesSent(Integer filesSent) {
        this.filesSent = filesSent;
    }

    public String getIssues() {
        return issues;
    }

    public void setIssues(String issues) {
        this.issues = issues;
    }

    public Pc getPc() {
        return pc;
    }

    public void setPc(Pc pc) {
        this.pc = pc;
    }
}