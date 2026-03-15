package com.dabom.messaging.kafka.event.dto.notification;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;

public final class NotificationEventSupport {
    private NotificationEventSupport() {}

    public static EventEnvelope<NotificationPayload> toEnvelope(NotificationPayload payload) {
        return EventEnvelope.of(KafkaEventTypes.NOTIFICATION, payload);
    }
}
