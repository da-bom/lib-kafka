package com.project.global.kafka.error;

// 메시지 파싱/역직렬화 실패를 명확히 구분하기 위한 예외.
public class KafkaMessageDeserializationException extends RuntimeException {
    public KafkaMessageDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
