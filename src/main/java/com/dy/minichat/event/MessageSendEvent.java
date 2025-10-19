package com.dy.minichat.event;

import com.dy.minichat.dto.message.TalkMessageDTO;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageSendEvent {
    private TalkMessageDTO talkMessage;
}