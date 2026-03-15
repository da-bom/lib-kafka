package com.dabom.messaging.kafka.metrics.producer;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.metrics.KafkaMetrics;
import com.dabom.messaging.kafka.support.KafkaEventMetadataExtractor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaMetricsProducerListener implements ProducerListener<String, String> {
    private final KafkaMetrics kafkaMetrics;
    private final KafkaEventMetadataExtractor kafkaEventMetadataExtractor;

    @Override
    public void onSuccess(ProducerRecord<String, String> producerRecord, RecordMetadata metadata) {
        String eventType =
                kafkaEventMetadataExtractor.extractNormalizedEventType(producerRecord.value());
        kafkaMetrics.incrementProducerSendSuccess(producerRecord.topic(), eventType);
    }

    @Override
    public void onError(
            ProducerRecord<String, String> producerRecord, RecordMetadata metadata, Exception ex) {
        String eventType =
                kafkaEventMetadataExtractor.extractNormalizedEventType(producerRecord.value());
        kafkaMetrics.incrementProducerSendError(producerRecord.topic(), eventType);
    }
}
