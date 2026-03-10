package com.dabom.messaging.kafka.contract;

public final class KafkaConsumerGroups {
    public static final String DABOM_PROCESSOR_USAGE_MAIN =
            "dabom-processor-usage-main-group";
    public static final String DABOM_PROCESSOR_USAGE_POLICY =
            "dabom-processor-usage-policy-group";
    public static final String DABOM_PROCESSOR_USAGE_PERSISTENCE =
            "dabom-processor-usage-persistence-group";
    public static final String DABOM_API_CORE_REALTIME =
            "dabom-api-core-realtime-group";
    public static final String DABOM_NOTIFICATION_SENDER =
            "dabom-notification-sender-group";

    private KafkaConsumerGroups() {}
}
