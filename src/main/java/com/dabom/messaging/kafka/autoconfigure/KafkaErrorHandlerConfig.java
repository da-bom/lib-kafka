package com.dabom.messaging.kafka.autoconfigure;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

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

import com.dabom.messaging.kafka.error.KafkaErrorAction;
import com.dabom.messaging.kafka.error.KafkaErrorDecision;
import com.dabom.messaging.kafka.error.KafkaExceptionClassifier;
import com.dabom.messaging.kafka.metrics.KafkaMetrics;
import com.dabom.messaging.kafka.support.KafkaEventMetadataExtractor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class KafkaErrorHandlerConfig {
    private static final String UNKNOWN_GROUP = "unknown";

    private final KafkaMetrics kafkaMetrics;
    private final KafkaEventMetadataExtractor kafkaEventMetadataExtractor;
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
        KafkaTemplate<String, String> dltKafkaTemplate =
                new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(dltProducerConfig()));

        DeadLetterPublishingRecoverer deadLetterPublishingRecoverer =
                new DeadLetterPublishingRecoverer(
                        dltKafkaTemplate,
                        (consumerRecord, exception) ->
                                new TopicPartition(
                                        consumerRecord.topic() + ".DLT",
                                        consumerRecord.partition()));

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

        ConsumerRecordRecoverer recoverer =
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    String eventType =
                            kafkaEventMetadataExtractor.extractNormalizedEventType(consumerRecord);

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

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, createRetryBackOff());
        errorHandler.setBackOffFunction(
                (consumerRecord, exception) -> {
                    KafkaErrorDecision decision = kafkaExceptionClassifier.classify(exception);
                    if (decision.action() == KafkaErrorAction.RETRY) {
                        return createRetryBackOff();
                    }
                    return new FixedBackOff(0L, 0L);
                });
        errorHandler.setRetryListeners(
                (consumerRecord, exception, deliveryAttempt) ->
                        kafkaMetrics.incrementRetryableError(
                                consumerRecord.topic(),
                                UNKNOWN_GROUP,
                                kafkaEventMetadataExtractor.extractNormalizedEventType(
                                        consumerRecord)));
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

    private BackOff createRetryBackOff() {
        ExponentialBackOffWithMaxRetries retryBackOff =
                new ExponentialBackOffWithMaxRetries(maxRetryAttempts);
        retryBackOff.setInitialInterval(retryInitialIntervalMs);
        retryBackOff.setMultiplier(retryMultiplier);
        retryBackOff.setMaxInterval(retryMaxIntervalMs);
        return retryBackOff;
    }
}
