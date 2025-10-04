package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class UserChatIdGenerator extends AbstractIdGenerator {

    public UserChatIdGenerator(Snowflake snowflake) {
        super(snowflake);

    }
}