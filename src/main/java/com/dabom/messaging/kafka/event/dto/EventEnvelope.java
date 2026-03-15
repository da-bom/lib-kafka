package com.dabom.messaging.kafka.event.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.dabom.messaging.kafka.event.dto.policy.PolicyUpdatedPayload;
import com.dabom.messaging.kafka.event.dto.usage.UsagePayload;
import com.dabom.messaging.kafka.event.dto.usage.UsageRealtimePayload;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        LocalDateTime timestamp,
        @JsonTypeInfo(
                        use = JsonTypeInfo.Id.NAME,
                        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                        property = "eventType",
                        defaultImpl = Void.class)
                @JsonSubTypes({
                    @JsonSubTypes.Type(value = UsagePayload.class, name = KafkaEventTypes.DATA_USAGE),
                    @JsonSubTypes.Type(value = PolicyUpdatedPayload.class, name = KafkaEventTypes.POLICY_UPDATED),
                    @JsonSubTypes.Type(value = NotificationPayload.class, name = KafkaEventTypes.NOTIFICATION),
                    @JsonSubTypes.Type(value = UsageRealtimePayload.class, name = KafkaEventTypes.USAGE_REALTIME)
                })
                T payload) {
    public static <T> EventEnvelope<T> of(String eventType, T payload) {
        return new EventEnvelope<>(UUID.randomUUID().toString(), eventType, LocalDateTime.now(), payload);
    }
}
