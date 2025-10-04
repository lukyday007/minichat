package com.dy.minichat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@ToString
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "chats")
public class Chat {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "chatstatus")
    private ChatStatus status = ChatStatus.DIRECT;

    private String title = "untitled";

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

}