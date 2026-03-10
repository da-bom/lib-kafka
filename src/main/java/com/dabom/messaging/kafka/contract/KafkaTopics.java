package com.dabom.messaging.kafka.contract;

public final class KafkaTopics {
    public static final String USAGE_EVENTS = "usage-events";
    public static final String POLICY_UPDATED = "policy-updated";
    public static final String USAGE_PERSIST = "usage-persist";
    public static final String USAGE_REALTIME = "usage-realtime";
    public static final String NOTIFICATION = "notification-events";

    private KafkaTopics() {}
}
