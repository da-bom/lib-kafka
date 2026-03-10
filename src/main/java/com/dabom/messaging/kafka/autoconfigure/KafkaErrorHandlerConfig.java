package com.dabom.messaging.kafka.autoconfigure;

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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dabom.messaging.kafka.error.KafkaErrorAction;
import com.dabom.messaging.kafka.error.KafkaErrorDecision;
import com.dabom.messaging.kafka.error.KafkaExceptionClassifier;
import com.dabom.messaging.kafka.metrics.KafkaMetricTagSanitizer;
import com.dabom.messaging.kafka.metrics.KafkaMetrics;

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
        // DLT 발행도 문자열 payload 기준으로 통일한다.
        KafkaTemplate<String, String> dltKafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(dltProducerConfig()));

        // 기본 규칙은 원본 topic 이름에 .DLT를 붙인다.
        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (consumerRecord, exception) ->
                                new TopicPartition(
                                        consumerRecord.topic() + ".DLT",
                                        consumerRecord.partition()));

        // 분류 결과를 헤더에 남겨 운영/분석 시 원인 추적이 가능하게 한다.
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

        // IGNORE는 DLT 없이 메트릭만 남기고 종료한다.
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

        // 기본 재시도 backoff는 exponential 전략을 사용한다.
        ExponentialBackOffWithMaxRetries backOff =
                new ExponentialBackOffWithMaxRetries(maxRetryAttempts);
        backOff.setInitialInterval(retryInitialIntervalMs);
        backOff.setMultiplier(retryMultiplier);
        backOff.setMaxInterval(retryMaxIntervalMs);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.setBackOffFunction(
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    // RETRY만 backoff를 적용하고 나머지는 즉시 recoverer로 넘긴다.
                    if (decision.action() == KafkaErrorAction.RETRY) {
                        return createRetryBackOff();
                    }
                    return new FixedBackOff(0L, 0L);
                });
        errorHandler.setRetryListeners(
                // 재시도 횟수는 메트릭으로 집계한다.
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
            // 태그 추출 실패가 에러 핸들러 동작 자체를 깨뜨리면 안 된다.
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
