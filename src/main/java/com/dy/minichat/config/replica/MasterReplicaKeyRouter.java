package com.dy.minichat.config.replica;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class MasterReplicaKeyRouter {
    @Getter
    @Setter
    private String masterKey = "";
    private final List<String> replicaKeys = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public void addReplicaKey(String key) {
        replicaKeys.add(key);
    }

    public String getReplicaKey() {
    /*
        동시성 문제 발생 방지 로직 적용
    */
        int current = counter.updateAndGet(i -> i >= replicaKeys.size() - 1 ? 0 : i + 1); // 동시성 문제 없음
        return replicaKeys.get(current);
    }
}