package com.dy.minichat.component;

import jakarta.annotation.PreDestroy;

public class WebSocketCleaner_미완 {
    //@PreDestroy
    public void clean(){
        // for
        // 붙어있는 모든 웹소켓을 하나씩 돌면서 .close();
        // Thread.sleep 적당히 걸면서
        // 1초에 1000개씩 끊는다.
        // 10만개의 소켓을 끊기위해서 100초 사용됨 -> graceful shutdown
    }

}
