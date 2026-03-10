package com.dabom.messaging.kafka.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KafkaExceptionClassifierTest {

    private final KafkaExceptionClassifier classifier = new KafkaExceptionClassifier();

    @Test
    @DisplayName("IllegalArgumentException is classified as IGNORE")
    void classifyIllegalArgumentExceptionAsIgnore() {
        KafkaErrorDecision decision = classifier.classify(new IllegalArgumentException("invalid"));

        assertEquals(KafkaErrorAction.IGNORE, decision.action());
        assertEquals(KafkaErrorCode.INVALID_EVENT, decision.code());
    }

    @Test
    @DisplayName("Deserialization exception is classified as DLQ")
    void classifyDeserializationAsDlq() {
        KafkaErrorDecision decision =
                classifier.classify(
                        new KafkaMessageDeserializationException(
                                "parse failed", new RuntimeException("cause")));

        assertEquals(KafkaErrorAction.DLQ, decision.action());
        assertEquals(KafkaErrorCode.DESERIALIZATION_FAILED, decision.code());
    }

    @Test
    @DisplayName("Socket timeout is classified as RETRY")
    void classifySocketTimeoutAsRetry() {
        KafkaErrorDecision decision = classifier.classify(new SocketTimeoutException("timeout"));

        assertEquals(KafkaErrorAction.RETRY, decision.action());
        assertEquals(KafkaErrorCode.TRANSIENT_NETWORK, decision.code());
    }

    @Test
    @DisplayName("Unknown runtime exception is classified as DLQ")
    void classifyUnknownAsDlq() {
        KafkaErrorDecision decision = classifier.classify(new RuntimeException("unexpected"));

        assertEquals(KafkaErrorAction.DLQ, decision.action());
        assertEquals(KafkaErrorCode.UNKNOWN, decision.code());
    }
}
