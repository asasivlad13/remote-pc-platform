package com.remote.repository;

import com.remote.model.ConnectionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConnectionLogRepository extends JpaRepository<ConnectionLog, Long> {
    List<ConnectionLog> findByUsernameOrderByTimestampDesc(String username);
}