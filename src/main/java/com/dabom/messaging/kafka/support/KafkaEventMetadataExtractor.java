package com.dabom.messaging.kafka.support;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.metrics.KafkaMetricTagSanitizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaEventMetadataExtractor {
    private static final ZoneId ASIA_SEOUL = ZoneId.of("Asia/Seoul");

    private final ObjectMapper objectMapper;

    public String extractNormalizedEventType(String payload) {
        if (payload == null || payload.isBlank()) {
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String rawEventType =
                    root.path("eventType").asText(KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE);
            return KafkaMetricTagSanitizer.normalizeEventType(rawEventType);
        } catch (Exception exception) {
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }
    }

    public String extractNormalizedEventType(ConsumerRecord<?, ?> consumerRecord) {
        Object rawValue = consumerRecord.value();
        if (!(rawValue instanceof String payload)) {
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }
        return extractNormalizedEventType(payload);
    }

    public Instant extractProducedAt(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }

        try {
            String timestamp = objectMapper.readTree(payload).path("timestamp").asText();
            if (timestamp == null || timestamp.isBlank()) {
                return null;
            }
            return LocalDateTime.parse(timestamp).atZone(ASIA_SEOUL).toInstant();
        } catch (Exception exception) {
            return null;
        }
    }
}
