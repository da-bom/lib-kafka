package com.dabom.messaging.kafka.event.publisher;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.event.KafkaEventMessageSupport;
import com.dabom.messaging.kafka.event.dto.EventEnvelope;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DefaultKafkaEventPublisher implements KafkaEventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaEventMessageSupport kafkaEventMessageSupport;

    @Override
    public <T> void publish(String topic, EventEnvelope<T> envelope) {
        kafkaTemplate.send(topic, kafkaEventMessageSupport.serialize(envelope));
    }

    @Override
    public <T> void publish(String topic, String eventType, T payload) {
        publish(topic, EventEnvelope.of(eventType, payload));
    }
}
