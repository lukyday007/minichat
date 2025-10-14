package com.dy.minichat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT) // <-- 이 부분을 수정!
@ActiveProfiles("local1")
// 테스트 시 gRPC 서버는 비활성화하여 포트 충돌을 원천 방지
@TestPropertySource(properties = "grpc.server.port=-1")
class MinichatApplicationTests {

	@Test
	void contextLoads() {
	}

}
