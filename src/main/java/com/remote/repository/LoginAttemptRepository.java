package com.remote.repository;

import com.remote.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {
    Optional<LoginAttempt> findByIpAddress(String ipAddress);
}