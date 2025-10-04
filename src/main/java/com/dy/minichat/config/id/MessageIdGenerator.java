package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class MessageIdGenerator extends AbstractIdGenerator{
    public MessageIdGenerator(Snowflake snowflake) {
        super(snowflake);
    }
}