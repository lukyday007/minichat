package com.dy.minichat.repository;

import com.dy.minichat.entity.UndeliveredMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UndeliveredMessageRepository extends JpaRepository<UndeliveredMessage, Long> {

    List<UndeliveredMessage> findByReceiverIdAndIsDeliveredFalse(Long receiverId);
}