package com.dy.minichat.config.id;

import com.dy.minichat.global.id.Snowflake;

public abstract class AbstractIdGenerator {
    protected final Snowflake snowflake;

    protected AbstractIdGenerator(Snowflake snowflake) {
        this.snowflake = snowflake;
    }

    public long generate(){
        return snowflake.nextId();
    }
}