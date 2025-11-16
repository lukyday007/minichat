package com.dy.minichat.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@ToString
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "userchats")
public class UserChat {
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    // 메세지 읽은 사람 표기 컬럼
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_id")
    private Message lastReadMessage;

    // [추가] 내용을 가져오기 위한 마지막 메시지 참조
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_written_message_id")
    private Message lastWrittenMessage; // last_message_id

    // [추가] 채팅방 목록 내려줄 때 -> 최신 채팅방부터 내려주기
    // 최근 메세지가 쌓인 채팅방이 제일 위에 있는 채팅방 정렬
    @Column(name = "last_message_timestamp")
    private LocalDateTime lastMessageTimestamp; // 마지막 메시지가 쓰여진 시간

    @Column(name = "is_deleted")
    private boolean isDeleted = false;

    @CreationTimestamp // DB에 데이터가 저장되는 순간의 시간이 createdAt 필드에 자동으로 기록
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}