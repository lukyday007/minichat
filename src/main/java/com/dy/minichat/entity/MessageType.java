package com.dy.minichat.entity;

public enum MessageType {
    TALK,        // 일반 사용자 대화
    SYSTEM_ENTRY, // 시스템 입장 알림
    SYSTEM_LEAVE  // 시스템 퇴장 알림
}