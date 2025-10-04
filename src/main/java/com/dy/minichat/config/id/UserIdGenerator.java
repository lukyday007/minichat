package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class UserIdGenerator extends AbstractIdGenerator{
    public UserIdGenerator(Snowflake snowflake) {
        super(snowflake);
    }
}