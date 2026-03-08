package com.project.global.kafka.error;

public enum KafkaErrorCode {
    // 네트워크/브로커 계열의 일시적 오류.
    TRANSIENT_NETWORK,
    // DB 계열의 일시적 오류.
    TRANSIENT_DB,
    // 스키마/도메인 검증 실패 이벤트.
    INVALID_EVENT,
    // 역직렬화(파싱) 실패.
    DESERIALIZATION_FAILED,
    // 비즈니스 처리 단계 실패.
    PROCESSING_FAILED,
    // 분류 규칙에 없는 예외.
    UNKNOWN
}
