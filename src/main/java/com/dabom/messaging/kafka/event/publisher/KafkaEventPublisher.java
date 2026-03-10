package com.dabom.messaging.kafka.event.publisher;

import com.dabom.messaging.kafka.event.dto.EventEnvelope;

public interface KafkaEventPublisher {
    <T> void publish(String topic, EventEnvelope<T> envelope);

    <T> void publish(String topic, String eventType, T payload);

    <T> void publish(String topic, String eventType, String subType, T payload);
}
