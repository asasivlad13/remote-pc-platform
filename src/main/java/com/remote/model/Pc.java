package com.remote.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pcs")
public class Pc {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String macAddress;

    @Enumerated(EnumType.STRING)
    private PcStatus status;

    private LocalDateTime lastConnection;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Конструкторы
    public Pc() {}

    public Pc(String name, String macAddress, User user) {
        this.name = name;
        this.macAddress = macAddress;
        this.user = user;
        this.status = PcStatus.OFFLINE;
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

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}