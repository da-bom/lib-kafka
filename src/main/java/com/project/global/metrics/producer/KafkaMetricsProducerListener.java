package com.project.global.metrics.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.global.metrics.KafkaMetricTagSanitizer;
import com.project.global.metrics.KafkaMetrics;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaMetricsProducerListener implements ProducerListener<String, String> {
    private final KafkaMetrics kafkaMetrics;
    private final ObjectMapper objectMapper;

    // 성공
    @Override
    public void onSuccess(ProducerRecord<String, String> producerRecord, RecordMetadata metadata) {
        String eventType = extractEventType(producerRecord.value());
        kafkaMetrics.incrementProducerSendSuccess(producerRecord.topic(), eventType);
    }

    // 실패
    @Override
    public void onError(
            ProducerRecord<String, String> producerRecord, RecordMetadata metadata, Exception ex) {
        String eventType = extractEventType(producerRecord.value());
        kafkaMetrics.incrementProducerSendError(producerRecord.topic(), eventType);
    }

    private String extractEventType(String value) {
        if (value == null || value.isBlank()) {
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }

        try {
            String rawEventType =
                    objectMapper
                            .readTree(value)
                            .path("eventType")
                            .asText(KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE);
            return KafkaMetricTagSanitizer.normalizeEventType(rawEventType);
        } catch (Exception exception) {
            // Metrics extraction must never break the producer flow.
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }
    }
}
