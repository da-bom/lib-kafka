package com.project.global.kafka.error;

public enum KafkaErrorAction {
    // 일시적 장애는 백오프 기반 재시도.
    RETRY,
    // 복구 불필요한 이벤트는 무시하고 오프셋 진행.
    IGNORE,
    // 즉시 처리 불가한 이벤트는 DLQ로 이동.
    DLQ
}
