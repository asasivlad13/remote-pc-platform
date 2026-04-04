package com.remote.repository;

import com.remote.model.Pc;
import com.remote.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PcRepository extends JpaRepository<Pc, Long> {
    List<Pc> findByUser(User user);
}