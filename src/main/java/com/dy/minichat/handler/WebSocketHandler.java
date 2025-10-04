package com.dy.minichat.handler;

import com.dy.minichat.dto.message.WebSocketMessageDTO;
import com.dy.minichat.dto.request.LastReadMessageRequestDTO;
import com.dy.minichat.dto.request.MessageRequestDTO;
import com.dy.minichat.entity.Message;
import com.dy.minichat.repository.UserRepository;
import com.dy.minichat.service.ChatService;
import com.dy.minichat.service.MessageService;
import com.dy.minichat.service.UserService;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;

    private final ChatService chatService;
    private final MessageService messageService;

    private final StringRedisTemplate redisTemplate;
    private final String serverIdentifier; // ServerConfig에서 생성된 Bean
    private static final String USER_SERVER_KEY_PREFIX = "ws:user:server:";

    /*
        [사용자: 채팅방 클릭]
        |
        +-----> 1. getMessagesWithUnreadCnt (과거 기록 조회) -> 여기서 수집한 chatId, userId등을 redis로 보냄
        |
        +-----> 2. WebSocket 연결 (실시간 통신 준비) -> 여기서 redis를 통해 chatId 받아오기 -> 가능..?
        |
        V
        [앱: 화면에 과거 메시지 표시 & 실시간 수신 대기 상태]
        |
        V
        3. updateLastReadMessage (다 읽었다고 서버에 기록)
    */

    // 세션 관리 : (userId -> session) 맵으로 변경
    // afterConnectionEstablished와 afterConnectionClosed에서 관리
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

            // 로컬 메모리에 세션 저장 (메시지 전송을 위해 필수)
            userIdToSessionMap.put(userId, session);
            log.info("[연결 수립] 사용자 ID: {}, 세션 ID: {}", userId, session.getId());

            // [추가] Redis에 "어떤 유저가 / 이 서버에 접속했다"는 정보 저장
            String redisKey = USER_SERVER_KEY_PREFIX + userId;
            redisTemplate.opsForValue().set(redisKey, serverIdentifier, 12, TimeUnit.HOURS); // TTL 설정과 함께 저장
            log.info("[연결 수립] Redis에 사용자 위치 정보 저장. Key: {}, Server: {}", redisKey, serverIdentifier);

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

        /*
            String payload = message.getPayload();
            log.info("payload: {}", payload);

            for(WebSocketSession ss: sessions) { // creating broadcast server
                ss.sendMessage(new TextMessage(payload));
            }
            String messageType;
            switch (messageType) {
                case "send_message":
                    break;
                case "sfsdfdsf":
                    break;
            }
        */
        // 세션에서 보낸 사람의 ID를 안전하게 가져오기
        Optional<Long> senderIdOptional = getUserIdFromSession(session);
        if (senderIdOptional.isEmpty()) {
            log.warn("userId가 없는 비정상 세션(ID: {})으로부터 메시지 수신 시도. 무시합니다.", session.getId());
            return; // userId가 없으면 아무 처리도 하지 않음
        }
        Long senderId = senderIdOptional.get();

        String payload = message.getPayload();
        WebSocketMessageDTO webSocketMessageDTO = objectMapper.readValue(payload, WebSocketMessageDTO.class);
        Long chatId = webSocketMessageDTO.getChatId();

        log.info("Received DTO (JSON): {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(webSocketMessageDTO));

        switch (webSocketMessageDTO.getType()) {

            case TALK:
                // AppService 호출하여 메시지 DB에 저장
                messageService.createMessage(
                        new MessageRequestDTO(webSocketMessageDTO.getContent()), senderId, chatId
                );
                // 해당 채팅방의 모든 세션에게 메시지 방송
                sendMessageToChatRoom(webSocketMessageDTO, chatId);
                log.info("[메시지] 보낸사람: {}, 채팅방: {}, 내용: {}", senderId, chatId, webSocketMessageDTO.getContent());
                break;

            // 향후 다른 실시간 메시지 타입(예: READ_ACK)이 추가.
            default:
                log.warn("처리할 수 없는 메시지 타입({}) 수신", webSocketMessageDTO.getType());
                break;

        }
    }

    // 특정 채팅방에 메시지를 방송하는 헬퍼 메서드
    private void sendMessageToChatRoom(WebSocketMessageDTO message, Long chatId) {
        Set<Long> userIdsInChat = chatService.getUsersInChat(chatId);

        if (userIdsInChat == null || userIdsInChat.isEmpty()) {
            log.warn("메시지를 전송할 사용자가 없습니다. (채팅방 ID: {})", chatId);
            return;
        }

        // 스프링 프레임워크에 내장된 클래스
        TextMessage textMessage;
        try {
            // 메시지 DTO를 JSON 문자열로 변환하여 TextMessage 객체 생성 (한 번만 수행)
            textMessage = new TextMessage(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.error("메시지 DTO JSON 변환 실패. ChatId: {}", chatId, e);
            return;
        }

        // 각 유저 ID에 해당하는 WebSocketSession을 찾아 메시지 전송.
        userIdsInChat.parallelStream().forEach(userId -> {
            WebSocketSession receiverSession = userIdToSessionMap.get(userId);
            if (receiverSession != null && receiverSession.isOpen()) {
                try {
                    receiverSession.sendMessage(textMessage);
                    log.info("로컬 메시지 전송 성공. 수신자 ID: {}", userId);

                } catch (IOException e) {
                    log.error("메시지 전송 실패. 수신자 ID: {}", userId, e);
                }
            } else {
                String redisKey = USER_SERVER_KEY_PREFIX + userId;
                String serverId = redisTemplate.opsForValue().get(redisKey);

                if (serverId != null && !serverId.equals(serverIdentifier)) {

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
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // redis del

        Optional<Long> userIdOptional = getUserIdFromSession(session);

        if (userIdOptional.isPresent()) {
            Long userId = userIdOptional.get();

            userIdToSessionMap.remove(userId);
            log.info("[연결 종료] 사용자 ID: {} 연결이 끊겼습니다. 상태: {}", userId, status);

            // (선택) chatService에 비정상 종료를 알려 상태를 정리하도록 할 수 있습니다.
            // chatService.handleDisconnect(userId);

            // [추가] Redis에 저장된 사용자 위치 정보 삭제
            String redisKey = USER_SERVER_KEY_PREFIX + userId;
            redisTemplate.delete(redisKey);
            log.info("[연결 종료] Redis 사용자 위치 정보 삭제. Key: {}", redisKey);



        }
    }

    private Optional<Long> getUserIdFromSession(WebSocketSession session) {
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

    private void sendMessageToOthersInChatRoom(WebSocketMessageDTO message, Set<WebSocketSession> sessions, WebSocketSession senderSession) {
        sessions.stream()
                .filter(sess -> !sess.getId().equals(senderSession.getId()))
                .forEach(sess -> {
                    try {
                        String payload = objectMapper.writeValueAsString(message);
                        sess.sendMessage(new TextMessage(payload));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                });
    }
}