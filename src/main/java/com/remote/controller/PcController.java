package com.remote.controller;

import com.remote.dto.PcResponseDto;
import com.remote.model.Pc;
import com.remote.model.User;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pcs")
public class PcController {

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<PcResponseDto> getMyPcs() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();

        List<Pc> pcs = pcRepository.findByUser(user);

        // Преобразуем Pc в PcResponseDto (без циклических ссылок)
        return pcs.stream()
                .map(pc -> new PcResponseDto(
                        pc.getId(),
                        pc.getName(),
                        pc.getMacAddress(),
                        pc.getStatus(),
                        pc.getLastConnection()
                ))
                .collect(Collectors.toList());
    }
}