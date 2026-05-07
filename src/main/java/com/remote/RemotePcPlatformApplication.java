package com.remote;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class RemotePcPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(RemotePcPlatformApplication.class, args);
	}

}
