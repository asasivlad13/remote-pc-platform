package com.remote.controller;

import com.remote.model.Pc;
import com.remote.model.User;
import com.remote.repository.PcRepository;
import com.remote.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/pcs")
public class PcController {

    @Autowired
    private PcRepository pcRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public List<Pc> getMyPcs() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow();
        return pcRepository.findByUser(user);
    }
}