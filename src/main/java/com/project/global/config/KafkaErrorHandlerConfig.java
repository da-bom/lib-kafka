package com.project.global.config;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.global.kafka.error.KafkaErrorAction;
import com.project.global.kafka.error.KafkaErrorDecision;
import com.project.global.kafka.error.KafkaExceptionClassifier;
import com.project.global.metrics.KafkaMetricTagSanitizer;
import com.project.global.metrics.KafkaMetrics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaErrorHandlerConfig {
    private static final String UNKNOWN_GROUP = "unknown";

    private final KafkaMetrics kafkaMetrics;
    private final ObjectMapper objectMapper;
    private final KafkaExceptionClassifier kafkaExceptionClassifier;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${app.kafka.error-handler.retry.max-attempts:2}")
    private int maxRetryAttempts;

    @Value("${app.kafka.error-handler.retry.initial-interval-ms:1000}")
    private long retryInitialIntervalMs;

    @Value("${app.kafka.error-handler.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${app.kafka.error-handler.retry.max-interval-ms:10000}")
    private long retryMaxIntervalMs;

    @Bean
    public CommonErrorHandler kafkaCommonErrorHandler() {
        // DLQ 발행은 문자열 key/value를 그대로 보내는 전용 템플릿 사용.
        KafkaTemplate<String, String> dltKafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(dltProducerConfig()));

        // 기본 규칙: 원본 토픽 + ".DLT", 원본 파티션 유지.
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (consumerRecord, exception) ->
                                new TopicPartition(
                                        consumerRecord.topic() + ".DLT", consumerRecord.partition()));

        // 운영/분석을 위해 분류 결과를 헤더로 남긴다.
        deadLetterPublishingRecoverer.setHeadersFunction(
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    RecordHeaders headers = new RecordHeaders();
                    headers.add(
                            "x-error-code",
                            decision.code().name().getBytes(StandardCharsets.UTF_8));
                    headers.add(
                            "x-error-action",
                            decision.action().name().getBytes(StandardCharsets.UTF_8));
                    return headers;
                });

        // 무시 정책은 DLQ로 보내지 않고 메트릭만 기록한다.
        ConsumerRecordRecoverer recoverer =
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    String eventType = extractEventType(consumerRecord);

                    if (decision.action() == KafkaErrorAction.IGNORE) {
                        kafkaMetrics.incrementInvalidEvent(
                                consumerRecord.topic(), UNKNOWN_GROUP, eventType);
                        log.warn(
                                "Ignored kafka record. topic={}, partition={}, offset={}, code={}",
                                consumerRecord.topic(),
                                consumerRecord.partition(),
                                consumerRecord.offset(),
                                decision.code());
                        return;
                    }

                    deadLetterPublishingRecoverer.accept(consumerRecord, exception);
                    kafkaMetrics.incrementDlt(consumerRecord.topic(), UNKNOWN_GROUP, eventType);
                };

        // Kafka 리스너가 던진 예외를 받는
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(maxRetryAttempts);
        backOff.setInitialInterval(retryInitialIntervalMs);
        backOff.setMultiplier(retryMultiplier);
        backOff.setMaxInterval(retryMaxIntervalMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setBackOffFunction(
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    // RETRY만 재시도 백오프를 적용하고, 나머지는 즉시 recoverer로 보낸다.
                    if (decision.action() == KafkaErrorAction.RETRY) {
                        return createRetryBackOff();
                    }
                    return new FixedBackOff(0L, 0L);
                });
        errorHandler.setRetryListeners(
                // 재시도 시도 횟수를 메트릭으로 집계한다.
                (consumerRecord, exception, deliveryAttempt) ->
                        kafkaMetrics.incrementRetryableError(
                                consumerRecord.topic(),
                                UNKNOWN_GROUP,
                                extractEventType(consumerRecord)));
        errorHandler.setCommitRecovered(true);

        return errorHandler;
    }

    private Map<String, Object> dltProducerConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return config;
    }

    private String extractEventType(ConsumerRecord<?, ?> consumerRecord) {
        Object rawValue = consumerRecord.value();
        if (!(rawValue instanceof String payload) || payload.isBlank()) {
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String rawEventType =
                    root.path("eventType").asText(KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE);
            return KafkaMetricTagSanitizer.normalizeEventType(rawEventType);
        } catch (Exception exception) {
            // 파싱 실패 시 태그 카디널리티 보호를 위해 UNKNOWN으로 통일.
            return KafkaMetricTagSanitizer.UNKNOWN_EVENT_TYPE;
        }
    }

    private BackOff createRetryBackOff() {
        ExponentialBackOffWithMaxRetries retryBackOff =
                new ExponentialBackOffWithMaxRetries(maxRetryAttempts);
        retryBackOff.setInitialInterval(retryInitialIntervalMs);
        retryBackOff.setMultiplier(retryMultiplier);
        retryBackOff.setMaxInterval(retryMaxIntervalMs);
        return retryBackOff;
    }
}
