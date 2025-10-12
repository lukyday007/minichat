package com.dy.minichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class ServerConfig {
    @Value("${server.identifier}")
    private String identifier;

    @Bean
    public String serverIdentifier() {
        // 주입받은 값을 그대로 Bean으로 등록
        return identifier;
    }
}