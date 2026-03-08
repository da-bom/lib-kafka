package com.project.global.kafka.error;

import java.net.SocketTimeoutException;

import org.apache.kafka.common.errors.RetriableException;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.errors.TimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.stereotype.Component;

@Component
public class KafkaExceptionClassifier {

    // 예외 체인을 순회해 최종 처리 정책(action/code)을 결정한다.
    public KafkaErrorDecision classify(Exception exception) {
        Throwable throwable = exception;

        if (hasCause(
                throwable,
                KafkaMessageDeserializationException.class,
                DeserializationException.class,
                SerializationException.class)) {
            return new KafkaErrorDecision(
                    KafkaErrorAction.DLQ, KafkaErrorCode.DESERIALIZATION_FAILED);
        }

        if (hasCause(throwable, IllegalArgumentException.class)) {
            return new KafkaErrorDecision(KafkaErrorAction.IGNORE, KafkaErrorCode.INVALID_EVENT);
        }

        if (hasCause(
                throwable,
                TimeoutException.class,
                SocketTimeoutException.class,
                RetriableException.class)) {
            return new KafkaErrorDecision(KafkaErrorAction.RETRY, KafkaErrorCode.TRANSIENT_NETWORK);
        }

        if (hasCause(throwable, TransientDataAccessException.class)) {
            return new KafkaErrorDecision(KafkaErrorAction.RETRY, KafkaErrorCode.TRANSIENT_DB);
        }

        if (hasCause(throwable, KafkaMessageProcessingException.class)) {
            return new KafkaErrorDecision(KafkaErrorAction.RETRY, KafkaErrorCode.PROCESSING_FAILED);
        }

        return new KafkaErrorDecision(KafkaErrorAction.DLQ, KafkaErrorCode.UNKNOWN);
    }

    @SafeVarargs
    private static boolean hasCause(
            Throwable throwable, Class<? extends Throwable>... targetTypes) {
        // wrapper 예외가 여러 번 감싸도 원인 타입을 찾기 위해 체인 전체를 검사한다.
        Throwable current = throwable;
        while (current != null) {
            for (Class<? extends Throwable> targetType : targetTypes) {
                if (targetType.isInstance(current)) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
