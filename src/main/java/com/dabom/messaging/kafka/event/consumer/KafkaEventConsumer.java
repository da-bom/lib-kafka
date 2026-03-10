package com.dabom.messaging.kafka.event.consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.fasterxml.jackson.core.type.TypeReference;
import com.dabom.messaging.kafka.event.KafkaEventMessageSupport;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;

public interface KafkaEventConsumer<T> {
    String eventType();

    TypeReference<EventEnvelope<T>> typeReference();

    void handle(EventEnvelope<T> envelope, String recordKey);

    default void consume(
            ConsumerRecord<String, String> consumerRecord,
            KafkaEventMessageSupport kafkaEventMessageSupport) {
        kafkaEventMessageSupport.consumeByEventType(
                consumerRecord,
                eventType(),
                typeReference(),
                this::handle);
    }
}
