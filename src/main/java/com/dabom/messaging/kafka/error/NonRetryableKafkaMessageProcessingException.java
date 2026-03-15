package com.dabom.messaging.kafka.error;

public class NonRetryableKafkaMessageProcessingException extends RuntimeException {
    public NonRetryableKafkaMessageProcessingException(String message) {
        super(message);
    }

    public NonRetryableKafkaMessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
