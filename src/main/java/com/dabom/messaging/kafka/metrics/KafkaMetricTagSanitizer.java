package com.dabom.messaging.kafka.metrics;

import java.util.Set;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;

public final class KafkaMetricTagSanitizer {
    public static final String UNKNOWN_EVENT_TYPE = "UNKNOWN";
    public static final String OTHER_EVENT_TYPE = "OTHER";

    private static final Set<String> ALLOWED_EVENT_TYPES =
            Set.of(
                    KafkaEventTypes.DATA_USAGE,
                    KafkaEventTypes.POLICY_UPDATED,
                    KafkaEventTypes.USAGE_PERSIST,
                    KafkaEventTypes.NOTIFICATION,
                    KafkaEventTypes.USAGE_REALTIME);

    private KafkaMetricTagSanitizer() {}

    public static String normalizeEventType(String rawEventType) {
        if (rawEventType == null || rawEventType.isBlank()) {
            return UNKNOWN_EVENT_TYPE;
        }
        if (ALLOWED_EVENT_TYPES.contains(rawEventType)) {
            return rawEventType;
        }
        return OTHER_EVENT_TYPE;
    }
}
