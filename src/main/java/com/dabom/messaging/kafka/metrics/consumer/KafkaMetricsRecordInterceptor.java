package com.dabom.messaging.kafka.metrics.consumer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import com.dabom.messaging.kafka.metrics.KafkaMetrics;
import com.dabom.messaging.kafka.support.KafkaEventMetadataExtractor;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class KafkaMetricsRecordInterceptor implements RecordInterceptor<String, String> {
    private static final String START_NANOS_HEADER = "x-metrics-start-nanos";

    private final KafkaMetrics kafkaMetrics;
    private final KafkaEventMetadataExtractor kafkaEventMetadataExtractor;

    @Override
    @Nullable
    public ConsumerRecord<String, String> intercept(
            ConsumerRecord<String, String> consumerRecord, Consumer<String, String> consumer) {
        setStartNanosHeader(consumerRecord);

        String eventType = kafkaEventMetadataExtractor.extractNormalizedEventType(consumerRecord);
        Instant producedAt = kafkaEventMetadataExtractor.extractProducedAt(consumerRecord.value());

        kafkaMetrics.recordProducerToConsumerLatency(
                consumerRecord.topic(),
                consumer.groupMetadata().groupId(),
                eventType,
                producedAt,
                Instant.now());

        return consumerRecord;
    }

    @Override
    public void success(
            ConsumerRecord<String, String> consumerRecord, Consumer<String, String> consumer) {
        recordProcessingMetrics(consumerRecord, consumer, true);
    }

    @Override
    public void failure(
            ConsumerRecord<String, String> consumerRecord,
            Exception ex,
            Consumer<String, String> consumer) {
        recordProcessingMetrics(consumerRecord, consumer, false);
    }

    private void recordProcessingMetrics(
            ConsumerRecord<String, String> consumerRecord,
            Consumer<String, String> consumer,
            boolean success) {
        long started = getStartNanos(consumerRecord);
        String eventType = kafkaEventMetadataExtractor.extractNormalizedEventType(consumerRecord);
        String group = consumer.groupMetadata().groupId();

        if (success) {
            kafkaMetrics.incrementSuccess(consumerRecord.topic(), group, eventType);
        }

        kafkaMetrics.recordProcessingTime(
                consumerRecord.topic(),
                group,
                eventType,
                Duration.ofNanos(System.nanoTime() - started));
    }

    private void setStartNanosHeader(ConsumerRecord<String, String> consumerRecord) {
        Headers headers = consumerRecord.headers();
        headers.remove(START_NANOS_HEADER);
        headers.add(
                START_NANOS_HEADER,
                Long.toString(System.nanoTime()).getBytes(StandardCharsets.UTF_8));
    }

    private long getStartNanos(ConsumerRecord<String, String> consumerRecord) {
        var header = consumerRecord.headers().lastHeader(START_NANOS_HEADER);
        if (header == null || header.value() == null) {
            return System.nanoTime();
        }

        try {
            return Long.parseLong(new String(header.value(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            return System.nanoTime();
        }
    }
}
