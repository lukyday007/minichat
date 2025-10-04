package com.dy.minichat.global.id;

public class Snowflake {
    private static final int UNUSED_SIGH_BITS = 1;
    private static final int EPOCH_BITS = 41;
    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;
    private static final long DEFAULT_CUSTOM_EPOCH = 1735689600000L; // 2025-01-01 00:00:00 UTC
    private final long nodeId;
    private final long customEpoch;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0L;

    public Snowflake(long nodeId) {
        this.nodeId = nodeId;
        this.customEpoch = DEFAULT_CUSTOM_EPOCH;
    }

    public synchronized long nextId() {
        long currentTimestamp = System.currentTimeMillis() - customEpoch;

        if (currentTimestamp == lastTimestamp) {
            sequence = sequence + 1;
        } else {
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return currentTimestamp << (NODE_ID_BITS + SEQUENCE_BITS)
                | (nodeId << SEQUENCE_BITS)
                | sequence;
    }

    public long[] parse(long id) {
        long maskNodeId = ((1L << NODE_ID_BITS) - 1) << SEQUENCE_BITS;
        long maskSequence = (1L << SEQUENCE_BITS) - 1;

        long timestamp = (id >> (NODE_ID_BITS + SEQUENCE_BITS)) + customEpoch;
        long nodeId = (id & maskNodeId) >> SEQUENCE_BITS;
        long sequence = id & maskSequence;

        return new long[]{timestamp, nodeId, sequence};
    }
}