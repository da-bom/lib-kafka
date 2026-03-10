package com.dabom.messaging.kafka.event.dto.notification;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;

public final class NotificationEventSupport {
    private NotificationEventSupport() {}

    public static String resolveSubType(NotificationPayload payload) {
        return switch (payload) {
            case QuotaUpdatedPayload ignored -> NotificationSubTypes.QUOTA_UPDATED;
            case CustomerBlockedPayload ignored -> NotificationSubTypes.CUSTOMER_BLOCKED;
            case ThresholdAlertPayload ignored -> NotificationSubTypes.THRESHOLD_ALERT;
        };
    }

    public static EventEnvelope<NotificationPayload> toEnvelope(NotificationPayload payload) {
        return EventEnvelope.of(KafkaEventTypes.NOTIFICATION, resolveSubType(payload), payload);
    }
}
