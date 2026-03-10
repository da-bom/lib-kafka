package com.dabom.messaging.kafka.contract;

public final class KafkaEventTypes {
    public static final String DATA_USAGE = "DATA_USAGE";
    public static final String POLICY_UPDATED = "POLICY_UPDATED";
    public static final String USAGE_PERSIST = "USAGE_PERSIST";
    public static final String USAGE_REALTIME = "USAGE_REALTIME";
    public static final String NOTIFICATION = "NOTIFICATION";

    private KafkaEventTypes() {}
}
