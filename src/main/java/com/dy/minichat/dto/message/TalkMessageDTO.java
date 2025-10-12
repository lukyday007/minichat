package com.dy.minichat.dto.message;

import lombok.*;
import java.time.Instant;

/*
    WebSocketMessage DTO의 역할: Request인가? Response인가?
    WebSocketMessage는 실시간 통신에서 Request와 Response 역할을 모두 수행하는 '통신용 DTO'.

*/
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TalkMessageDTO {
    // readResponseDTO, leaveResponseDTO
    // 아래의 필드들은 응답별로 구분
    // 메시지의 종류 (입장, 대화, 읽음 등)

    private Long senderId;

    private Long chatId;

    private MessageType type;

    private String content;

    // 마지막으로 읽은 메시지 ID (READ 타입일 때 사용)
    private Long lastMessageId;

    private Instant timestamp;

    // 메시지 타입을 관리하기 위한 Enum
    public enum MessageType {
        TALK
    }
}