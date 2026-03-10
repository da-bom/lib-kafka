package com.dabom.messaging.kafka.event.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.dabom.messaging.kafka.contract.KafkaEventTypes;
import com.dabom.messaging.kafka.event.dto.notification.NotificationPayload;
import com.dabom.messaging.kafka.event.dto.policy.PolicyUpdatedPayload;
import com.dabom.messaging.kafka.event.dto.usage.UsagePayload;
import com.dabom.messaging.kafka.event.dto.usage.UsagePersistPayload;
import com.dabom.messaging.kafka.event.dto.usage.UsageRealtimePayload;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        String subType,
        LocalDateTime timestamp,
        @JsonTypeInfo(
                        use = JsonTypeInfo.Id.NAME,
                        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                        property = "eventType",
                        defaultImpl = Void.class)
                @JsonSubTypes({
                    @JsonSubTypes.Type(value = UsagePayload.class, name = KafkaEventTypes.DATA_USAGE),
                    @JsonSubTypes.Type(value = PolicyUpdatedPayload.class, name = KafkaEventTypes.POLICY_UPDATED),
                    @JsonSubTypes.Type(value = UsagePersistPayload.class, name = KafkaEventTypes.USAGE_PERSIST),
                    @JsonSubTypes.Type(value = NotificationPayload.class, name = KafkaEventTypes.NOTIFICATION),
                    @JsonSubTypes.Type(value = UsageRealtimePayload.class, name = KafkaEventTypes.USAGE_REALTIME)
                })
                T payload) {
    // subType이 필요 없는 이벤트를 위한 기본 생성 헬퍼다.
    public static <T> EventEnvelope<T> of(String eventType, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(), eventType, null, LocalDateTime.now(), payload);
    }

    // 세부 분류가 필요한 이벤트를 위한 생성 헬퍼다.
    public static <T> EventEnvelope<T> of(String eventType, String subType, T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                subType,
                LocalDateTime.now(),
                payload);
    }
}
