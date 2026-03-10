package com.dabom.messaging.kafka.error;

public class KafkaMessageProcessingException extends RuntimeException {
    public KafkaMessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
