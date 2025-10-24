package com.dy.minichat.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserChatJdbcRepository {
    private final JdbcTemplate jdbcTemplate;

    public void batchUpdateLastWrittenMessage(List<Long> userChatIds, Long lastMessageId, LocalDateTime timestamp) {

        /*
            레이스 컨디션(Race Condition)으로 인한 데이터 덮어쓰기 방지
            AND (last_written_message_id IS NULL OR last_written_message_id < ?
            추가

            -> 성능차이 많이 남! = 레디스 파이프라인
        */

        String sql = "UPDATE user_chat " +
                "SET last_written_message_id = ?, last_message_timestamp = ? " +
                "WHERE id = ? AND is_deleted = false " +
                "AND (last_written_message_id IS NULL OR last_written_message_id < ?)";
        jdbcTemplate.batchUpdate(sql, userChatIds, 1000, (ps, userChatId) -> {
            ps.setLong(1, lastMessageId);
            ps.setTimestamp(2, Timestamp.valueOf(timestamp));
            ps.setLong(3, userChatId);               // WHERE id = ?
            ps.setLong(4, lastMessageId);            // WHERE ... last_written_message_id < ?        });
        });
    }


    public static class UserChatUpdate {
        private final Long userId;
        private final Long chatId;
        private final Long lastMessageId;
        private final String dirtyKey;

        public UserChatUpdate(Long userId, Long chatId, Long lastMessageId, String dirtyKey) {
            this.userId = userId;
            this.chatId = chatId;
            this.lastMessageId = lastMessageId;
            this.dirtyKey = dirtyKey;
        }

        public Long getUserId() { return userId; }
        public Long getChatId() { return chatId; }
        public Long getLastMessageId() { return lastMessageId; }
        public String getDirtyKey() { return dirtyKey; }
    }

    public void batchUpdateLastRead(List<UserChatUpdate> updates) {
        String sql = "UPDATE userchats " +
                "SET last_read_message_id = ? " +
                "WHERE user_id = ? AND chat_id = ? " +
                "AND (last_read_message_id IS NULL OR last_read_message_id < ?)";

        jdbcTemplate.batchUpdate(sql,
                updates,
                updates.size(),
                (ps, update) -> {
                    ps.setLong(1, update.getLastMessageId());
                    ps.setLong(2, update.getUserId());
                    ps.setLong(3, update.getChatId());
                    ps.setLong(4, update.getLastMessageId());
                }
        );
    }
}