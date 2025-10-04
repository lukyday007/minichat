package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class ChatIdGenerator extends AbstractIdGenerator {

    public ChatIdGenerator(Snowflake snowflake) {
        super(snowflake);
    }

}