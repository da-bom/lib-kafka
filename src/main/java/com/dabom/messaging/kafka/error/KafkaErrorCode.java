package com.dabom.messaging.kafka.error;

public enum KafkaErrorCode {
    // 네트워크나 브로커 계열의 일시적 장애다.
    TRANSIENT_NETWORK,
    // 데이터 저장소 계열의 일시적 장애다.
    TRANSIENT_DB,
    // 이벤트 자체가 계약에 맞지 않는다.
    INVALID_EVENT,
    // 역직렬화 또는 파싱 단계에서 실패했다.
    DESERIALIZATION_FAILED,
    // 비즈니스 처리 단계에서 실패했다.
    PROCESSING_FAILED,
    NON_RETRYABLE_PROCESSING_FAILED,
    // 분류 규칙에 없는 예외다.
    UNKNOWN
}
