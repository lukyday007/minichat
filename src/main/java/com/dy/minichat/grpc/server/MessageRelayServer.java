package com.dy.minichat.grpc.server;

import com.dy.grpc.proto.RelayMessageRequest;
import com.dy.grpc.proto.RelayMessageResponse;
import com.dy.grpc.proto.RelayMessageServiceGrpc;
import com.dy.minichat.component.WebSocketSessionManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@GrpcService // gRPC 서비스임을 나타내는 어노테이션
@RequiredArgsConstructor
public class MessageRelayServer extends RelayMessageServiceGrpc.RelayMessageServiceImplBase {

    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Override
    public void relayMessage(RelayMessageRequest request, StreamObserver<RelayMessageResponse> responseObserver) {
        log.info("gRPC relayMessage 요청 수신: {}", request);

        Long recipientId = request.getRecipientId(); // proto에 recipientId 추가
        WebSocketSession receiverSession = sessionManager.getSession(recipientId);

        if (receiverSession != null && receiverSession.isOpen()) {
            try {
                // gRPC 요청을 다시 WebSocket이 이해할 수 있는 DTO/JSON 형태로 변환
                // 서버 간 통신이므로, 클라이언트가 보낸 DTO와 동일한 구조를 만들어줌.
                String messagePayload = buildMessagePayload(request);

                receiverSession.sendMessage(new TextMessage(messagePayload));
                log.info("gRPC -> WebSocket 메시지 릴레이 성공. 수신자 ID: {}", recipientId);

                // 성공 응답 전송
                RelayMessageResponse response = RelayMessageResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("Message relayed successfully to user " + recipientId)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();

            } catch (IOException e) {
                log.error("gRPC -> WebSocket 메시지 전송 실패. 수신자 ID: {}", recipientId, e);
                // 실패 응답 전송
                responseObserver.onError(e);
            }
        } else {
            log.warn("메시지를 수신할 세션이 없습니다 (오프라인이거나 다른 서버에 있을 수 있음). 수신자 ID: {}", recipientId);
            RelayMessageResponse response = RelayMessageResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Receiver session not found or closed for user " + recipientId)
                    .build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    // gRPC Request 객체로부터 WebSocket으로 보낼 JSON 문자열을 생성하는 헬퍼 메서드
    private String buildMessagePayload(RelayMessageRequest request) throws JsonProcessingException {
        // TalkMessageDTO와 유사한 구조의 Map 또는 객체를 만들어 JSON으로 변환
        // protobuf의 timestamp는 String이므로 Instant로 파싱
        Map<String, Object> payloadMap = Map.of(
                "type", request.getMessageType(),
                "senderId", request.getSenderId(),
                "chatId", request.getChatId(),
                "content", request.getContent(),
                "timestamp", Instant.parse(request.getTimestamp())
        );
        return objectMapper.writeValueAsString(payloadMap);
    }
}
