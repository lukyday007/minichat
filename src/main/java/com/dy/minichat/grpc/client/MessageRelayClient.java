package com.dy.minichat.grpc.client;

import com.dy.grpc.proto.RelayBulkMessageRequest;
import com.dy.grpc.proto.RelayMessageResponse;
import com.dy.grpc.proto.RelayMessageServiceGrpc;
import com.dy.grpc.proto.RelayMessageRequest;
import com.dy.minichat.dto.message.TalkMessageDTO;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
    서버 (송신 측) grpc client 코드
*/
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRelayClient {

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public void relayMessageToServer (String targetServerHost, int targetServerPort, TalkMessageDTO messageDto, Long recipientId) {
        String targetAddress = targetServerHost + ":" + targetServerPort;
        ManagedChannel channel = channels.computeIfAbsent(targetAddress, key ->
                ManagedChannelBuilder.forAddress(targetServerHost, targetServerPort)
                        .usePlaintext()
                        .build()
        );

        RelayMessageServiceGrpc.RelayMessageServiceBlockingStub stub = RelayMessageServiceGrpc.newBlockingStub(channel);

        RelayMessageRequest request = RelayMessageRequest.newBuilder()
                .setSenderId(messageDto.getSenderId())
                .setChatId(messageDto.getChatId())
                .setContent(messageDto.getContent())
                .setMessageType(messageDto.getType().name())
                .setTimestamp(messageDto.getTimestamp().toString())
                .setRecipientId(recipientId)
                .build();

        try {
            RelayMessageResponse response = stub.relayMessage(request);
            log.info("gRPC Relay Result: {}", response.getMessage());

        } catch (Exception e) {
            log.error("gRPC request to {} failed: {}", targetAddress, e.getMessage());
            channels.remove(targetAddress);
            channel.shutdown();
        }
    }

    /**
     * [추가] 벌크 릴레이 메서드
     */
    public void relayBulkMessageToServer(String targetServerHost, int targetServerPort, TalkMessageDTO messageDto, List<Long> recipientIds) {
        String targetAddress = targetServerHost + ":" + targetServerPort;
        ManagedChannel channel = channels.computeIfAbsent(targetAddress, key ->
                ManagedChannelBuilder.forAddress(targetServerHost, targetServerPort)
                        .usePlaintext()
                        .build()
        );

        RelayMessageServiceGrpc.RelayMessageServiceBlockingStub stub = RelayMessageServiceGrpc.newBlockingStub(channel);

        // [변경] RelayBulkMessageRequest 사용
        RelayBulkMessageRequest request = RelayBulkMessageRequest.newBuilder()
                .setSenderId(messageDto.getSenderId())
                .setChatId(messageDto.getChatId())
                .setContent(messageDto.getContent())
                .setMessageType(messageDto.getType().name())
                .setTimestamp(messageDto.getTimestamp().toString())
                .addAllRecipientIds(recipientIds) // [변경] 단일 ID 대신 리스트(repeated) 추가
                .build();

        try {
            // [변경] stub.relayBulkMessage 호출
            RelayMessageResponse response = stub.relayBulkMessage(request);
            log.info("gRPC Bulk Relay Result: {}", response.getMessage());

        } catch (Exception e) {
            log.error("gRPC bulk request to {} failed: {}", targetAddress, e.getMessage());
            // 채널에 문제가 생겼을 수 있으므로 제거 (다음 요청 시 새로 생성)
            channels.remove(targetAddress);
            channel.shutdown();
        }
    }

    @PreDestroy
    public void shutdownAllChannels() {
        log.info("애플리케이션 종료: 모든 활성 gRPC 채널을 종료합니다...");
        for (ManagedChannel channel : channels.values()) {
            try {
                channel.shutdown();
            } catch (Exception e) {
                log.error("gRPC 채널 종료 중 에러 발생", e);
            }
        }
        log.info("모든 gRPC 채널이 종료되었습니다.");
    }
}