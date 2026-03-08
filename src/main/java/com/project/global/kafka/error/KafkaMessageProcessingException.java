package com.project.global.kafka.error;

// 파싱 이후 비즈니스 처리 단계 실패를 나타내는 예외.
public class KafkaMessageProcessingException extends RuntimeException {
    public KafkaMessageProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
