package com.dy.minichat.handler;

import com.dy.minichat.config.id.UndeliveredMessageIdGenerator;
import com.dy.minichat.dto.message.TalkMessageDTO;
import com.dy.minichat.dto.request.MessageRequestDTO;
import com.dy.minichat.entity.UndeliveredMessage;
import com.dy.minichat.grpc.client.MessageRelayClient;
import com.dy.minichat.property.GrpcServerProperties;
import com.dy.minichat.repository.UndeliveredMessageRepository;
import com.dy.minichat.service.ChatService;
import com.dy.minichat.service.FcmPushService;
import com.dy.minichat.service.MessageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final ChatService chatService;
    private final MessageService messageService;
    private final FcmPushService fcmPushService;
    private final UndeliveredMessageRepository undeliveredMessageRepository;
    private final UndeliveredMessageIdGenerator undeliveredMessageIdGenerator;

    private final StringRedisTemplate redisTemplate;
    private final String serverIdentifier; // ServerConfig에서 생성된 Bean
    private static final String USER_SERVER_KEY_PREFIX = "ws:user:server:";

    private final MessageRelayClient messageRelayClient; // gRPC 클라이언트 주입
    private final GrpcServerProperties grpcServerProperties; // gRPC 서버 주소록 주입

    private final Map<Long, WebSocketSession> userIdToSessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        /*
            write lock
            userIdSessionMap.put(10L, session);
            redis.set(10, localHostIp());
        */
        // Handshake 인터셉터에서 userId를 넣어주기

        log.info("--- WebSocket Connection Established ---");
        log.info("Session ID: {}", session.getId());
        log.info("Connection URI: {}", session.getUri());
        log.info("Session Attributes: {}", session.getAttributes()); // 👈 HandshakeInterceptor가 넣어준 정보 확인
        log.info("------------------------------------");

        Optional<Long> userIdOptional = getUserIdFromSession(session);

        // userId가 존재할 경우에만 연결 수립 로직 진행
        if (userIdOptional.isPresent()) {
            Long userId = userIdOptional.get();
            String userKey = "user:" + userId + ":state";

            // redis 에서 chatId 조회
            String chatIdStr = (String) redisTemplate.opsForHash().get(userKey, "chatId");

            if (chatIdStr != null) {
                // 로컬 메모리에 세션 저장 (메시지 전송을 위해 필수)
                userIdToSessionMap.put(userId, session);
                // [추가] Redis에 "어떤 유저가 / 이 서버에 접속했다"는 정보 저장 및 갱신
                redisTemplate.opsForHash().put(userKey, "serverId", serverIdentifier);
                redisTemplate.opsForHash().put(userKey, "lastActive", LocalDateTime.now().toString());
                log.info("유저 {}가 채팅방 {}에 연결됨, server log = {}", userId, chatIdStr, serverIdentifier);
            } else {
                log.warn("유저 {}의 chatId 정보가 없음 — API 미호출 가능성 있음", userId);
            }

        } else {
            try {
                log.warn("세션에 userId 속성이 없어 연결을 종료합니다. (ID: {})", session.getId());
                session.close(CloseStatus.BAD_DATA.withReason("Invalid session: Missing userId"));
            } catch (Exception e) {
                log.error("세션 종료 중 에러 발생", e);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        // 세션에서 보낸 사람의 ID를 안전하게 가져오기
        Optional<Long> senderIdOptional = getUserIdFromSession(session);
        if (senderIdOptional.isEmpty()) {
            log.warn("userId가 없는 비정상 세션(ID: {})으로부터 메시지 수신 시도. 무시합니다.", session.getId());
            return; // userId가 없으면 아무 처리도 하지 않음
        }
        Long senderId = senderIdOptional.get();

        String payload = message.getPayload();
        TalkMessageDTO talkMessageDTO = objectMapper.readValue(payload, TalkMessageDTO.class);
        log.info("Received DTO (JSON): {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(talkMessageDTO));

        Optional<Long> chatIdOpt = getCurrentChatIdForUser(senderId);
        if (chatIdOpt.isEmpty() || !chatIdOpt.get().equals(talkMessageDTO.getChatId())) {
            log.warn("[메시지 무시] user:{} 의 Redis상 chatId: {} 가 없거나 다름 (아직 채팅방 입장 처리 안됨)", senderId, chatIdOpt.get());
            return;
        }
        Long chatId = chatIdOpt.get();

        talkMessageDTO.setSenderId(senderId);
        talkMessageDTO.setTimestamp(Instant.now());

        switch (talkMessageDTO.getType()) {

            case TALK:
                // DB 저장
                messageService.createMessage(
                        new MessageRequestDTO(talkMessageDTO.getContent()), senderId, chatId
                );

                // 해당 채팅방의 모든 세션에게 메시지 방송
                // kafkaProducer.send(new MessageSendEvent());
                // 컨슈머가 받아서 안정적 처리
                sendMessageToChatRoom(talkMessageDTO);
                log.info("[메시지] 보낸사람: {}, 채팅방: {}, 내용: {}", senderId, chatId, talkMessageDTO.getContent());
                break;

            // 향후 다른 실시간 메시지 타입(예: READ_ACK)이 추가.
            default:
                log.warn("처리할 수 없는 메시지 타입({}) 수신", talkMessageDTO.getType());
                break;

        }
    }

    // 특정 채팅방에 메시지를 방송하는 헬퍼 메서드
    private void sendMessageToChatRoom(TalkMessageDTO message) {
        Long chatId = message.getChatId();
        Set<Long> userIdsInChat = chatService.getUsersInChat(chatId);

        if (userIdsInChat == null || userIdsInChat.isEmpty()) {
            log.warn("메시지를 전송할 사용자가 없습니다. (채팅방 ID: {})", chatId);
            return;
        }

        // 스프링 프레임워크에 내장된 클래스
        String messagePayload;
        try {
            // 메시지 DTO를 JSON 문자열로 변환하여 TextMessage 객체 생성 (한 번만 수행)
            messagePayload = objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            log.error("메시지 DTO JSON 변환 실패. ChatId: {}", chatId, e);
            return;
        }
        TextMessage textMessage = new TextMessage(messagePayload);

        /*
            해쉬맵, -> grpc 리퀘스트를 한번에 전송 [ 최적화 ] => 이해정도
         */

        // 각 유저 ID에 해당하는 WebSocketSession을 찾아 메시지 전송.
        userIdsInChat.parallelStream().forEach(userId -> {
            WebSocketSession receiverSession = userIdToSessionMap.get(userId);

            // Case 1: 같은 서버 내에서 WebSocket 전송
            if (receiverSession != null && receiverSession.isOpen()) {
                try {
                    receiverSession.sendMessage(textMessage);
                    log.info("로컬 메시지 전송 성공. 수신자 ID: {}", userId);

                } catch (IOException e) {
                    log.error("메시지 전송 실패. 수신자 ID: {}", userId, e);
                }
            }
            else {
                String redisKey = USER_SERVER_KEY_PREFIX + userId;
                String targetServerId = redisTemplate.opsForValue().get(redisKey);

                // Case 2-1: 다른 서버에 연결 → gRPC 릴레이
                if (targetServerId != null && !targetServerId.equals(serverIdentifier)) {
                    log.info("다른 서버로 메시지 릴레이 시도. 수신자 ID: {}, 대상 서버: {}", userId, targetServerId);
                    relayMessageViaGrpc(targetServerId, message, userId);
                }

                // Case 2-2: 접속 정보 없음 → FCM
                else {
                    log.info("오프라인 사용자. FCM 알림 전송 시도. 수신자 ID: {}", userId);
                    UndeliveredMessage undeliveredMessage = UndeliveredMessage.builder()
                            .id(undeliveredMessageIdGenerator.generate())
                            .chatId(chatId)
                            .senderId(message.getSenderId())
                            .receiverId(userId)
                            .content(message.getContent())
                            .build();
                    undeliveredMessageRepository.save(undeliveredMessage);

                    fcmPushService.sendPushNotification(userId, message);
                }

                // 나한테 웹소켓이 없는경우 or 아예 웹소켓이 연결되지 않은경우
                /*
                if (redis.exist(session){
                    // targetServer.request(); -> server to server (grpc / http2)

                    // grpcClient.relayMessage(relayMessageRequest);

                    xxxxx.proto
                    relayMessageRequest {
                        ...
                    }

                    relayMessageResponse {
                        ...
                    }

                    rpc relayMessage relayMessageRequest relayMessageResponse

                    r

                    // socket
                    // http
                    // grpc (http2) -> 한 번 적용해보기 !
                } else {
                    // FCM (ios push, android push) 99.99
                }
                */
            }
        });

    }

    @Override
    public void afterConnectionClosed (WebSocketSession session, CloseStatus status) throws Exception {
        Optional<Long> userIdOptional = getUserIdFromSession(session);
        if (userIdOptional.isEmpty()) {
            log.info("[연결 종료] 연결이 끊겼습니다. 상태: {}", status);
            return;
        }

        Long userId = userIdOptional.get();
        userIdToSessionMap.remove(userId);

        // (선택) chatService에 비정상 종료를 알려 상태를 정리하도록 할 수 있습니다.
        // chatService.handleDisconnect(userId);

        // [추가] Redis에 저장된 사용자 위치 정보 삭제
        String userKey = "user:" + userId + ":state";
        String chatIdStr = (String) redisTemplate.opsForHash().get(userKey, "chatId");
        if (chatIdStr != null) {
            redisTemplate.opsForSet().remove("chatId:" + chatIdStr + ":userId", String.valueOf(userId));
        }

        redisTemplate.delete(userKey);
        log.info("[연결 종료] Redis 사용자 위치 정보 삭제. Key: {}", userKey);
    }

    /*
        메세지를 다른 서버에 보냄
        1000 방 , ㄱ 서버에 유저들이 잇는데 (약 100명), ㄴ 서버에 (50명)
        ㄱ 서버에 100 리퀘스트를 다 요청 상황 - 포문 방식
        ㄱ 서버에는 벌크로 요청하는 릴레이로 개선
     */
    private void relayMessageViaGrpc (String targetServerId, TalkMessageDTO messageDTO, Long recipientId) {
        // 1. 설정에서 서버 주소록을 가져오기
        Map<String, String> addresses = grpcServerProperties.getAddresses();
        String targetAddress = addresses.get(targetServerId);

        if (targetAddress == null || targetAddress.isEmpty()) {
            log.error("gRPC 릴레이 실패: 대상 서버 '{}'의 주소를 찾을 수 없습니다.", targetServerId);
            return;
        }

        try {
            // 2. 주소를 host와 port로 분리 (e.g., "localhost:9091")
            String[] parts = targetAddress.split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            // 3. MessageRelayClient를 사용하여 gRPC 요청 송신
            messageRelayClient.relayMessageToServer(host, port, messageDTO, recipientId);

        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            log.error("gRPC 릴레이 실패: 대상 서버 '{}'의 주소 형식이 올바르지 않습니다. (address: {})", targetServerId, targetAddress, e);
        } catch (Exception e) {
            log.error("gRPC 릴레이 중 예상치 못한 에러 발생. 대상 서버: {}", targetServerId, e);
        }
    }

    private Optional<Long> getUserIdFromSession (WebSocketSession session) {
        try {
            Map<String, Object> attributes = session.getAttributes();
            Object userIdObj = attributes.get("userId");

            // 속성 자체가 없는 경우
            if (userIdObj == null) {
                log.error("세션(ID: {})에 'userId' 속성이 존재하지 않습니다. HandshakeInterceptor 설정을 확인하세요.", session.getId());
                return Optional.empty();
            }


            // userId 속성이 존재하고, Long 타입인지 확인
            if (userIdObj instanceof Long) {
                return Optional.of((Long) userIdObj);
            }

            // 속성이 없거나 타입이 맞지 않으면 빈 Optional 반환
            return Optional.empty();

        } catch (Exception e) {
            log.error("세션에서 userId를 추출하는 중 에러 발생. Session ID: {}", session.getId(), e);
            return Optional.empty(); // 예외 발생 시에도 안전하게 빈 Optional 반환
        }
    }


    private Optional<Long> getCurrentChatIdForUser(Long userId) {
        try {
            String userKey = "user:" + userId + ":state"; // Hash: {chatId, serverId, lastActive}
            Object chatIdObj = redisTemplate.opsForHash().get(userKey, "chatId");
            if (chatIdObj == null) return Optional.empty();
            return Optional.of(Long.parseLong(chatIdObj.toString()));
        } catch (Exception e) {
            log.error("Redis에서 user:{} 의 chatId 조회 실패", userId, e);
            return Optional.empty();
        }
    }
}