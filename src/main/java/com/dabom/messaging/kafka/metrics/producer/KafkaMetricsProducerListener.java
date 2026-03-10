package com.dabom.messaging.kafka.metrics.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.dabom.messaging.kafka.metrics.KafkaMetricTagSanitizer;
import com.dabom.messaging.kafka.metrics.KafkaMetrics;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaMetricsProducerListener implements ProducerListener<String, String> {
    private final KafkaMetrics kafkaMetrics;
    private final ObjectMapper objectMapper;

    @Override
    public void onSuccess(ProducerRecord<String, String> producerRecord, RecordMetadata metadata) {
        // 메트릭 추출 실패가 전송 성공 자체를 바꾸면 안 된다.
        String eventType = extractEventType(producerRecord.value());
        kafkaMetrics.incrementProducerSendSuccess(producerRecord.topic(), eventType);
    }

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
            // 메트릭 추출 실패는 producer 흐름을 깨지 않고 UNKNOWN으로 대체한다.
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }
    }
}
