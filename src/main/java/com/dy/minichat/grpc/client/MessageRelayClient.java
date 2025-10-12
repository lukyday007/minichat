package com.dy.minichat.grpc.client;

import com.dy.grpc.proto.RelayMessageProto;
import com.dy.grpc.proto.RelayMessageResponse;
import com.dy.grpc.proto.RelayMessageServiceGrpc;
import com.dy.grpc.proto.RelayMessageRequest;
import com.dy.minichat.dto.message.TalkMessageDTO;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/*
    서버 (송신 측) grpc client 코드
*/
@Slf4j
@Service
public class MessageRelayClient {
    public void relayMessageToServer (String targetServerHost, int targetServerPort, TalkMessageDTO messageDto, Long recipientId) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(targetServerHost, targetServerPort)
                .usePlaintext()
                .build();

        RelayMessageServiceGrpc.RelayMessageServiceBlockingStub stub = RelayMessageServiceGrpc.newBlockingStub(channel);

        RelayMessageRequest request = RelayMessageRequest.newBuilder()
                .setSenderId(messageDto.getSenderId())
                .setChatId(messageDto.getChatId())
                .setContent(messageDto.getContent())
                .setMessageType(messageDto.getType().name())
                .setTimestamp(messageDto.getTimestamp().toString())
                .setRecipientId(recipientId)
                .build();

        RelayMessageResponse response = stub.relayMessage(request);
        log.info("relay result = {}", response.getMessage());

        channel.shutdown();
    }
}