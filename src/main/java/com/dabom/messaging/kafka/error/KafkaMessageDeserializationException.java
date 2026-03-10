package com.dabom.messaging.kafka.error;

public class KafkaMessageDeserializationException extends RuntimeException {
    public KafkaMessageDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
