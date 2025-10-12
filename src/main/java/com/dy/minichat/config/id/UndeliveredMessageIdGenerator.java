package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;
import org.springframework.stereotype.Component;

@Component
public class UndeliveredMessageIdGenerator extends AbstractIdGenerator {
    public UndeliveredMessageIdGenerator(Snowflake snowflake) {
        super(snowflake);

    }
}