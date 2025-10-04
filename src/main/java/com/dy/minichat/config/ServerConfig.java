package com.dy.minichat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Configuration
public class ServerConfig {
    @Value("${server.port}")
    private int port;

    @Bean // "이 메서드가 반환하는 객체를 'serverIdentifier'라는 이름의 Bean으로 등록해주세요."
    public String serverIdentifier() {
        try {
            // 로컬 IP 주소와 포트 번호를 조합하여 "192.168.0.10:8080" 같은 고유 식별자 생성
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            return hostAddress + ":" + port;
        } catch (UnknownHostException e) {
            // IP 주소를 가져오지 못할 경우를 대비한 예외 처리
            // 실제 운영 환경에서는 로깅을 추가하는 것이 좋습니다.
            return "unknown:" + port;
        }
    }
}