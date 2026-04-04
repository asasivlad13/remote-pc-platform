package com.remote.dto;

import com.remote.model.PcStatus;
import java.time.LocalDateTime;

public class PcResponseDto {
    private Long id;
    private String name;
    private String macAddress;
    private PcStatus status;
    private LocalDateTime lastConnection;

    // Конструкторы
    public PcResponseDto() {}

    public PcResponseDto(Long id, String name, String macAddress, PcStatus status, LocalDateTime lastConnection) {
        this.id = id;
        this.name = name;
        this.macAddress = macAddress;
        this.status = status;
        this.lastConnection = lastConnection;
    }

    // Геттеры и сеттеры
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getMacAddress() { return macAddress; }
    public void setMacAddress(String macAddress) { this.macAddress = macAddress; }

    public PcStatus getStatus() { return status; }
    public void setStatus(PcStatus status) { this.status = status; }

    public LocalDateTime getLastConnection() { return lastConnection; }
    public void setLastConnection(LocalDateTime lastConnection) { this.lastConnection = lastConnection; }
}