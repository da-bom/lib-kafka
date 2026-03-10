package com.dabom.messaging.kafka.error;

public enum KafkaErrorAction {
    // 일시적 오류로 보고 재시도한다.
    RETRY,
    // 복구 가치가 없는 이벤트로 보고 무시한다.
    IGNORE,
    // 현재 소비 흐름에서 처리할 수 없어 DLT로 보낸다.
    DLQ
}
